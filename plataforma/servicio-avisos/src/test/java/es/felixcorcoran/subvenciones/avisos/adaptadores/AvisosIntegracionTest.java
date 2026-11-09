package es.felixcorcoran.subvenciones.avisos.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.encaje.EncajesHttp;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Encajes;

/**
 * Los avisos enteros con PostgreSQL y Kafka de verdad: alta y confirmación por la API, una
 * convocatoria leída que llega por Kafka, el encaje por HTTP (simulado con lo que respondería el
 * servicio de encaje) y el correo en un buzón de prueba.
 */
@SpringBootTest(properties = "subvenciones.avisos.cron=-")
@Testcontainers(disabledWithoutDocker = true)
class AvisosIntegracionTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@ServiceConnection
	static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.0");

	static {
		if (DockerClientFactory.instance().isDockerAvailable()) {
			postgres.start();
			kafka.start();
		}
	}

	static final List<Correo.Mensaje> BUZON = new CopyOnWriteArrayList<>();
	static MockRestServiceServer encaje;

	@TestConfiguration
	static class Dobles {
		@Bean
		@Primary
		Correo buzon() {
			return BUZON::add;
		}

		/** El cliente HTTP de verdad, contra un servidor de encaje simulado */
		@Bean
		@Primary
		Encajes encajeSimulado() {
			RestClient.Builder builder = RestClient.builder().baseUrl("http://encaje");
			encaje = MockRestServiceServer.bindTo(builder).build();
			return new EncajesHttp(builder.build());
		}
	}

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private JdbcClient jdbc;

	private static final String PERFIL = """
			{"provincia":"ES617","municipio":"","forma":"autonomo","empleados":0,"antiguedad":"1a3","sector":"I"}""";

	private static final String LEIDA = """
			{"id":"%s","tipo":"convocatoria.leida","version":1,"ocurrido":"2026-11-09T07:30:00Z",
			 "convocatoria":{"codigo":"%s","titulo":"Bonos al comercio de Málaga","fin":"2099-12-31","enlace":"x",
			                 "ia":{"categoria":"ayuda","plazoFin":null,"resumen":"Bonos"}}}""";

	@Test
	void deLaConvocatoriaLeidaAlCorreoDeQuienEncaja() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

		// Alta y confirmación con el enlace del correo
		mvc.perform(post("/api/v1/suscripciones").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@ejemplo.es\",\"perfil\":" + PERFIL + ",\"acepto\":true}"))
				.andExpect(status().isAccepted());
		Matcher m = Pattern.compile("t=([A-Za-z0-9_-]{43})").matcher(BUZON.getLast().texto());
		assertThat(m.find()).isTrue();
		mvc.perform(post("/api/v1/suscripciones/confirmar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + m.group(1) + "\"}")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/suscripciones/" + m.group(1))).andExpect(status().isOk());

		// La primera pasada no avisa de lo que ya había
		mvc.perform(post("/api/v1/avisos/pasada")).andExpect(status().isOk());
		BUZON.clear();

		// Llega una convocatoria leída por Kafka
		kafkaTemplate.send("convocatorias.leidas", "A9", LEIDA.formatted(UUID.randomUUID(), "A9")).get();
		esperar(() -> jdbc.sql("SELECT count(*) FROM novedades WHERE codigo = 'A9'").query(Integer.class).single() == 1);

		// El encaje dice que puede pedirla
		encaje.expect(ExpectedCount.once(), requestTo("http://encaje/api/v1/encajes"))
				.andExpect(jsonPath("$.provincia").value("ES617"))
				.andRespond(withSuccess("""
						[{"codigo":"A9","titulo":"Bonos al comercio de Málaga","cierre":"2099-12-31","estado":"SI",
						  "motivos":[{"tipo":"BIEN","texto":"Es para tu zona"}]}]""", MediaType.APPLICATION_JSON));

		mvc.perform(post("/api/v1/avisos/pasada")).andExpect(status().isOk());

		assertThat(BUZON).singleElement().satisfies(correo -> {
			assertThat(correo.para()).isEqualTo("ana@ejemplo.es");
			assertThat(correo.asunto()).isEqualTo("Una ayuda nueva para tu negocio");
			assertThat(correo.texto()).contains("Puedes pedirla: Bonos al comercio de Málaga").contains("Es para tu zona");
			assertThat(correo.cabeceras()).containsKey("List-Unsubscribe");
		});
		encaje.verify();

		// La baja con un clic lo borra todo
		mvc.perform(post("/api/v1/baja/" + m.group(1))).andExpect(status().isOk());
		assertThat(jdbc.sql("SELECT count(*) FROM suscriptores").query(Integer.class).single()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM avisos").query(Integer.class).single()).isZero();
	}

	@Test
	void laPasadaAManoEsSoloParaAdministracionYLasSuscripcionesSonPublicas() throws Exception {
		MockMvc seguro = MockMvcBuilders.webAppContextSetup(contexto)
				.apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
		seguro.perform(post("/api/v1/avisos/pasada")).andExpect(status().isUnauthorized());
		seguro.perform(post("/api/v1/avisos/pasada")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
				.andExpect(status().isForbidden());
		// Sin token: el enlace del correo es la llave; uno que no existe da 404, no 401
		seguro.perform(get("/api/v1/suscripciones/" + "a".repeat(43))).andExpect(status().isNotFound());
		seguro.perform(post("/api/v1/baja/" + "a".repeat(43))).andExpect(status().isOk());
	}

	private static void esperar(BooleanSupplier condicion) throws InterruptedException {
		Instant limite = Instant.now().plusSeconds(30);
		while (!condicion.getAsBoolean()) {
			if (Instant.now().isAfter(limite)) throw new AssertionError("No ha pasado en 30 segundos");
			Thread.sleep(200);
		}
	}
}
