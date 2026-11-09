package es.felixcorcoran.subvenciones.recogida.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.recogida.adaptadores.salida.bdns.BdnsHttp;
import es.felixcorcoran.subvenciones.recogida.aplicacion.Bdns.ResultadoFicha;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Codigo;
import es.felixcorcoran.subvenciones.recogida.dominio.Ficha;

/** El adaptador de la BDNS con respuestas reales grabadas (bdns/ficha.json, bdns/busqueda.json) */
class BdnsHttpTest {

	private static final String BASE = "https://bdns.prueba/api";

	private MockRestServiceServer servidor;
	private BdnsHttp bdns;

	@BeforeEach
	void preparar() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		servidor = MockRestServiceServer.bindTo(builder).build();
		bdns = new BdnsHttp(builder.build(), new BdnsHttp.Ritmo(Duration.ZERO, Duration.ZERO));
	}

	private static String recurso(String ruta) throws IOException {
		try (InputStream in = BdnsHttpTest.class.getResourceAsStream(ruta)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void leeUnaFichaRealYLaDejaLimpia() throws Exception {
		servidor.expect(requestTo(BASE + "/convocatorias?numConv=940577"))
				.andRespond(withSuccess(recurso("/bdns/ficha.json"), MediaType.APPLICATION_JSON));

		ResultadoFicha r = bdns.ficha("940577");

		assertThat(r).isInstanceOf(ResultadoFicha.Leida.class);
		Ficha f = ((ResultadoFicha.Leida) r).ficha();
		assertThat(f.codigo()).isEqualTo("940577");
		assertThat(f.publicada()).isEqualTo(LocalDate.of(2026, 11, 6));
		assertThat(f.presupuesto()).isEqualByComparingTo(new BigDecimal("600000"));
		assertThat(f.regiones()).containsExactly(new Codigo("ES532", "Mallorca"));
		assertThat(f.sectores()).containsExactly(new Codigo("G", "COMERCIO AL POR MAYOR Y AL POR MENOR"));
		assertThat(f.sede()).isNull();
		assertThat(f.fin()).isNull();
		assertThat(f.textoFin()).startsWith("15 dies hàbils");
		assertThat(f.documentos()).hasSize(2);
	}

	@Test
	void unaFichaDeOtroCodigoOIncompletaEsUnFallo() throws Exception {
		servidor.expect(requestTo(BASE + "/convocatorias?numConv=1"))
				.andRespond(withSuccess(recurso("/bdns/ficha.json"), MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(BASE + "/convocatorias?numConv=940577"))
				.andRespond(withSuccess("{\"codigoBDNS\":\"940577\",\"descripcion\":\"x\"}", MediaType.APPLICATION_JSON));

		assertThat(bdns.ficha("1")).isEqualTo(new ResultadoFicha.Fallo("ficha incompleta"));
		assertThat(bdns.ficha("940577")).isEqualTo(new ResultadoFicha.Fallo("ficha incompleta"));
	}

	@Test
	void reintentaCuandoElFalloEsDeEllosYAlTercerFalloLoDaPorFallido() throws Exception {
		servidor.expect(ExpectedCount.twice(), requestTo(BASE + "/convocatorias?numConv=940577")).andRespond(withServerError());
		servidor.expect(requestTo(BASE + "/convocatorias?numConv=940577"))
				.andRespond(withSuccess(recurso("/bdns/ficha.json"), MediaType.APPLICATION_JSON));
		assertThat(bdns.ficha("940577")).isInstanceOf(ResultadoFicha.Leida.class);

		servidor.reset();
		servidor.expect(ExpectedCount.times(3), requestTo(BASE + "/convocatorias?numConv=940577")).andRespond(withServerError());
		assertThat(bdns.ficha("940577")).isInstanceOf(ResultadoFicha.Fallo.class);
		servidor.verify();
	}

	@Test
	void buscaPaginaAPaginaSoloParaEmpresas() throws Exception {
		servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/convocatorias/busqueda")))
				.andExpect(queryParam("page", "0"))
				.andExpect(queryParam("tiposBeneficiario", "3,4"))
				.andExpect(queryParam("fechaDesde", "05/11/2026"))
				.andRespond(withSuccess(recurso("/bdns/busqueda.json"), MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/convocatorias/busqueda")))
				.andExpect(queryParam("page", "1"))
				.andRespond(withSuccess("{\"content\":[{\"numeroConvocatoria\":\"999\"}],\"last\":true}", MediaType.APPLICATION_JSON));

		var codigos = bdns.buscar(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6));

		assertThat(codigos).hasSize(4).startsWith("940587").endsWith("999");
		servidor.verify();
	}
}
