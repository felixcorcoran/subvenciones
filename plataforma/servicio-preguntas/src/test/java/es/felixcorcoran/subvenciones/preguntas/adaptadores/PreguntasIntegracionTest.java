package es.felixcorcoran.subvenciones.preguntas.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.gemini.GeminiHttp;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.PalabrasComoVectores;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Descargas;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.LectorPdf;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Redactor;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.persistencia.EventosProcesadosPostgres;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.persistencia.IndicePostgres;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.persistencia.TransaccionesSpring;
import es.felixcorcoran.subvenciones.preguntas.dominio.Citas;

/**
 * El servicio entero con PostgreSQL + pgvector y Kafka de verdad. La IA es de mentira: vectores
 * por palabras en común y una redacción que cita el primer pasaje que le llega, para comprobar que
 * pgvector devuelve el pasaje que toca y que la cita sale con su página.
 */
@SpringBootTest(properties = "subvenciones.preguntas.cron=-")
@Testcontainers(disabledWithoutDocker = true)
class PreguntasIntegracionTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@ServiceConnection
	static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.0");

	static {
		if (DockerClientFactory.instance().isDockerAvailable()) {
			postgres.start();
			kafka.start();
		}
	}

	static final List<String> BASES = List.of(
			"Base 1. Objeto.\nAyudas a la modernización del comercio minorista del municipio de Málaga.",
			"Base 2. Beneficiarios.\nPodrán ser beneficiarias las personas trabajadoras autónomas y las pequeñas empresas con establecimiento abierto.",
			"Base 3. Cuantía.\nLa ayuda máxima será de tres mil euros por establecimiento comercial.",
			"Base 4. Plazo.\nEl plazo de presentación de solicitudes será de quince días hábiles.");

	@TestConfiguration
	static class IaDePrueba {
		@Bean
		@Primary
		ServicioDePreguntas servicioDePrueba(IndicePostgres indice, JdbcClient jdbc,
				org.springframework.transaction.support.TransactionTemplate tx) {
			Descargas descargas = id -> new byte[] { '%', 'P', 'D', 'F' };
			LectorPdf lector = pdf -> BASES;
			Redactor redactor = (titulo, pregunta, contexto) -> {
				String primera = contexto.getFirst().texto().split("\n")[1];
				return new Citas.Propuesta(true, primera, List.of(new Citas.CitaPropuesta(1, primera)), "prueba");
			};
			return new ServicioDePreguntas(indice, indice, descargas, lector, new PalabrasComoVectores(), redactor,
					new EventosProcesadosPostgres(jdbc), new TransaccionesSpring(tx));
		}
	}

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private JdbcClient jdbc;
	/** El de verdad se crea igualmente (sin clave): que el contexto arranque con él */
	@Autowired
	private GeminiHttp gemini;

	private static final String LEIDA = """
			{"id":"%s","tipo":"convocatoria.leida","version":1,"ocurrido":"2026-11-09T07:30:00Z",
			 "convocatoria":{"codigo":"P1","titulo":"Ayudas al comercio de Málaga","documento":{"id":20,"nombre":"bases.pdf"},
			                 "ia":{"categoria":"ayuda"}}}""";

	@Test
	void deLaConvocatoriaLeidaALaRespuestaConSuCitaYSuPagina() throws Exception {
		kafkaTemplate.send("convocatorias.leidas", "P1", LEIDA.formatted(UUID.randomUUID())).get();
		esperar(() -> jdbc.sql("SELECT count(*) FROM documentos WHERE codigo = 'P1'").query(Integer.class).single() == 1);

		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
		mvc.perform(post("/api/v1/indices")).andExpect(jsonPath("$.indexados").value(1));
		assertThat(jdbc.sql("SELECT count(*) FROM pasajes WHERE codigo = 'P1'").query(Integer.class).single()).isEqualTo(4);

		mvc.perform(post("/api/v1/convocatorias/P1/preguntas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"pregunta\":\"¿Cuánto es la ayuda máxima por establecimiento?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.consta").value(true))
				.andExpect(jsonPath("$.citas[0].pagina").value(3))
				.andExpect(jsonPath("$.citas[0].texto").value("La ayuda máxima será de tres mil euros por establecimiento comercial"));

		mvc.perform(post("/api/v1/convocatorias/P1/preguntas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"pregunta\":\"¿Pueden pedirla las personas autónomas?\"}"))
				.andExpect(jsonPath("$.citas[0].pagina").value(2));

		mvc.perform(post("/api/v1/convocatorias/NO-EXISTE/preguntas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"pregunta\":\"¿Plazo?\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void indexarAManoEsSoloParaAdministracionYPreguntarEsPublico() throws Exception {
		MockMvc seguro = MockMvcBuilders.webAppContextSetup(contexto)
				.apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
		seguro.perform(post("/api/v1/indices")).andExpect(status().isUnauthorized());
		seguro.perform(post("/api/v1/indices")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
				.andExpect(status().isForbidden());
		seguro.perform(post("/api/v1/convocatorias/NO-EXISTE/preguntas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"pregunta\":\"¿Plazo?\"}"))
				.andExpect(status().isNotFound());
	}

	private static void esperar(BooleanSupplier condicion) throws InterruptedException {
		Instant limite = Instant.now().plusSeconds(30);
		while (!condicion.getAsBoolean()) {
			if (Instant.now().isAfter(limite)) throw new AssertionError("No ha pasado en 30 segundos");
			Thread.sleep(200);
		}
	}
}
