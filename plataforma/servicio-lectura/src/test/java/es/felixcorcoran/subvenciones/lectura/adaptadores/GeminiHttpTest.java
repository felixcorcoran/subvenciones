package es.felixcorcoran.subvenciones.lectura.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.gemini.GeminiHttp;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.IaNoDisponible;
import tools.jackson.databind.json.JsonMapper;

/** La cadena de modelos de Gemini con respuestas simuladas */
class GeminiHttpTest {

	private static final String BASE = "https://gemini.prueba/v1beta/models";
	private static final String ANALISIS = """
			{"categoria":"ayuda","resumen":"Dinero para el comercio.","paraQuien":"Comercios","importe":"Hasta 3.000 €",
			 "importeMaximo":3000,"plazo":"Hasta el 15 de diciembre","plazoFin":"2026-12-15","requisitos":["Estar en Málaga"],
			 "gastos":[],"condiciones":{"autonomos":"si","sociedades":"si","emprendedores":"no_consta","maxEmpleados":null,
			 "antiguedadMinMeses":null,"antiguedadMaxMeses":null,"municipio":null},"dudas":null}""";

	private MockRestServiceServer servidor;
	private GeminiHttp gemini;

	@BeforeEach
	void preparar() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		servidor = MockRestServiceServer.bindTo(builder).build();
		gemini = new GeminiHttp(builder.build(), JsonMapper.builder().build(),
				new GeminiHttp.Ajustes("clave", List.of("modelo-a", "modelo-b"), Duration.ZERO, Duration.ZERO, Duration.ofMinutes(30)),
				Clock.systemUTC());
	}

	private static String respuesta(String texto) {
		return """
				{"candidates":[{"content":{"parts":[{"text":%s}]}}]}""".formatted(JsonMapper.builder().build().writeValueAsString(texto));
	}

	@Test
	void mandaElPdfYElContextoYDevuelveLoLeidoConElModelo() {
		servidor.expect(requestTo(BASE + "/modelo-a:generateContent"))
				.andExpect(header("x-goog-api-key", "clave"))
				.andExpect(jsonPath("$.contents[0].parts[0].inlineData.mimeType").value("application/pdf"))
				.andExpect(jsonPath("$.contents[0].parts[1].text").value("contexto"))
				.andExpect(jsonPath("$.generationConfig.responseSchema.required[0]").value("categoria"))
				.andRespond(withSuccess(respuesta(ANALISIS), MediaType.APPLICATION_JSON));

		var r = gemini.leer(new byte[] { 1, 2, 3 }, "contexto");

		assertThat(r.modelo()).isEqualTo("modelo-a");
		assertThat(r.analisis().plazoFin()).isEqualTo("2026-12-15");
		assertThat(r.analisis().valido()).isTrue();
	}

	@Test
	void conElCupoDelDiaAgotadoPasaAlSiguienteYNoLoVuelveAProbar() {
		servidor.expect(requestTo(BASE + "/modelo-a:generateContent"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("quota GenerateRequestsPerDayPerProjectPerModel"));
		servidor.expect(requestTo(BASE + "/modelo-b:generateContent"))
				.andRespond(withSuccess(respuesta(ANALISIS), MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(BASE + "/modelo-b:generateContent"))
				.andRespond(withSuccess(respuesta(ANALISIS), MediaType.APPLICATION_JSON));

		assertThat(gemini.leer(new byte[1], "x").modelo()).isEqualTo("modelo-b");
		assertThat(gemini.leer(new byte[1], "x").modelo()).isEqualTo("modelo-b");
		servidor.verify();
	}

	@Test
	void siNingunModeloPuedeResponderNoEsCulpaDeLaConvocatoria() {
		servidor.expect(requestTo(BASE + "/modelo-a:generateContent")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		servidor.expect(requestTo(BASE + "/modelo-b:generateContent")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> gemini.leer(new byte[1], "x"))
				.isInstanceOf(IaNoDisponible.class)
				.hasMessageContaining("modelo-a: saturado")
				.hasMessageContaining("modelo-b: no existe");
		// El saturado descansa y el que no existe no se vuelve a probar hoy: de momento no hay con qué leer
		assertThat(gemini.disponible()).isFalse();
	}

	@Test
	void unModeloSaturadoDescansaYLasSiguientesConvocatoriasVanDirectasAlSiguiente() {
		servidor.expect(ExpectedCount.once(), requestTo(BASE + "/modelo-a:generateContent"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		servidor.expect(ExpectedCount.times(2), requestTo(BASE + "/modelo-b:generateContent"))
				.andRespond(withSuccess(respuesta(ANALISIS), MediaType.APPLICATION_JSON));

		assertThat(gemini.leer(new byte[1], "x").modelo()).isEqualTo("modelo-b");
		assertThat(gemini.leer(new byte[1], "x").modelo()).isEqualTo("modelo-b");
		servidor.verify();
	}

	@Test
	void conTodosAgotadosDejaDeEstarDisponible() {
		servidor.expect(requestTo(BASE + "/modelo-a:generateContent")).andRespond(withStatus(HttpStatus.NOT_FOUND));
		servidor.expect(requestTo(BASE + "/modelo-b:generateContent"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("PerDay"));

		assertThatThrownBy(() -> gemini.leer(new byte[1], "x")).isInstanceOf(IaNoDisponible.class);
		assertThat(gemini.disponible()).isFalse();
	}

	@Test
	void unErrorDeLaPeticionCuentaComoIntentoDeLaConvocatoria() {
		servidor.expect(requestTo(BASE + "/modelo-a:generateContent"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("The document has no pages"));

		assertThatThrownBy(() -> gemini.leer(new byte[1], "x"))
				.isNotInstanceOf(IaNoDisponible.class)
				.hasMessageContaining("Gemini 400");
	}

	@Test
	void sinClaveNoEstaDisponible() {
		var sinClave = new GeminiHttp(RestClient.create(), JsonMapper.builder().build(),
				new GeminiHttp.Ajustes("", List.of("modelo-a"), Duration.ZERO, Duration.ZERO, Duration.ofMinutes(30)), Clock.systemUTC());
		assertThat(sinClave.disponible()).isFalse();
	}
}
