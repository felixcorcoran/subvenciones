package es.felixcorcoran.subvenciones.encaje.adaptadores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * La API REST con el servicio entero, contra PostgreSQL migrado por Flyway. Aquí no se escucha
 * Kafka, para que esta clase no consuma los mensajes de {@link KafkaEncajeTest}.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class ApiEncajeTest extends PruebaConContenedores {

	/** Hoy es 1 de diciembre de 2026 */
	@TestConfiguration
	static class RelojFijo {
		@Bean
		@Primary
		Clock relojFijo() {
			ZoneId madrid = ZoneId.of("Europe/Madrid");
			return Clock.fixed(LocalDate.of(2026, 12, 1).atStartOfDay(madrid).toInstant(), madrid);
		}
	}

	/** Málaga, autónomo solo, de 1 a 3 años, hostelería */
	private static final String BAR_EN_MALAGA = """
			{"provincia":"ES617","municipio":"","forma":"autonomo","empleados":0,"antiguedad":"1a3","sector":"I"}""";

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private JdbcClient jdbc;

	private MockMvc mvc;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
		jdbc.sql("DELETE FROM convocatorias").update();
	}

	private void guardar(String codigo, String json) throws Exception {
		mvc.perform(put("/api/v1/convocatorias/" + codigo).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isNoContent());
	}

	private static String convocatoria(String codigo, String region, String fin) {
		return """
				{"codigo":"%s","titulo":"Ayuda %s","beneficiarios":["PYME Y PERSONAS FÍSICAS QUE DESARROLLAN ACTIVIDAD ECONÓMICA"],
				 "sectores":[],"regiones":[{"codigo":"%s","nombre":"x"}],"fin":%s,
				 "ia":{"condiciones":{"autonomos":"si","sociedades":"si","emprendedores":"no_consta"}}}"""
				.formatted(codigo, codigo, region, fin == null ? "null" : "\"" + fin + "\"");
	}

	@Test
	void listaLasAbiertasConLasQuePuedePedirPrimeroYSinLasCerradas() throws Exception {
		guardar("A", convocatoria("A", "ES21", "2026-12-20")); // País Vasco: no es para Málaga
		guardar("B", convocatoria("B", "ES61", "2026-12-31")); // Andalucía
		guardar("C", convocatoria("C", "ES", null)); // toda España, sin fecha de cierre
		guardar("D", convocatoria("D", "ES617", "2026-11-30")); // cerró ayer

		mvc.perform(post("/api/v1/encajes").contentType(MediaType.APPLICATION_JSON).content(BAR_EN_MALAGA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].codigo").value("B"))
				.andExpect(jsonPath("$[0].estado").value("SI"))
				.andExpect(jsonPath("$[0].motivos[0].texto").value("Es para tu zona"))
				.andExpect(jsonPath("$[1].codigo").value("C"))
				.andExpect(jsonPath("$[1].estado").value("SI"))
				.andExpect(jsonPath("$[2].codigo").value("A"))
				.andExpect(jsonPath("$[2].estado").value("NO"))
				.andExpect(jsonPath("$[2].motivos[0].texto").value("Solo para negocios de País Vasco"));
	}

	@Test
	void guardarLaMismaConvocatoriaOtraVezLaSustituye() throws Exception {
		guardar("B", convocatoria("B", "ES21", "2026-12-31"));
		guardar("B", convocatoria("B", "ES61", "2026-12-31"));

		mvc.perform(post("/api/v1/encajes").contentType(MediaType.APPLICATION_JSON).content(BAR_EN_MALAGA))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].estado").value("SI"));
	}

	@Test
	void unaRespuestaQueNoEsDelFormularioEsUnaPeticionIncorrecta() throws Exception {
		mvc.perform(post("/api/v1/encajes").contentType(MediaType.APPLICATION_JSON)
				.content(BAR_EN_MALAGA.replace("autonomo", "cooperativa")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.dato").value("forma"));
	}
}
