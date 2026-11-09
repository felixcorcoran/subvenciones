package es.felixcorcoran.subvenciones.lectura.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Documentos;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.LectorIa;
import es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La lectura entera con PostgreSQL y Kafka de verdad, y la IA y la BDNS de mentira: lo que
 * publica la recogida se lee y sale como «convocatoria.leida» con todos sus datos más lo leído.
 */
@SpringBootTest(properties = { "subvenciones.lectura.cron=-", "subvenciones.outbox.cada=200ms" })
@Testcontainers(disabledWithoutDocker = true)
class LecturaIntegracionTest {

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

	@TestConfiguration
	static class IaDePrueba {
		@Bean
		@Primary
		LectorIa iaDePrueba() {
			return new LectorIa() {
				@Override
				public boolean disponible() {
					return true;
				}

				@Override
				public Resultado leer(byte[] pdf, String contexto) {
					return new Resultado(Ejemplos.analisis(), "modelo-de-prueba");
				}
			};
		}

		@Bean
		@Primary
		Documentos documentosDePrueba() {
			return id -> new byte[] { '%', 'P', 'D', 'F' };
		}
	}

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private JsonMapper json;

	private static final String PUBLICADA = """
			{"id":"%s","tipo":"convocatoria.publicada","version":1,"ocurrido":"2026-11-09T06:30:00Z",
			 "convocatoria":{"codigo":"L1","titulo":"Ayudas al comercio","organo":{"nivel1":"LOCAL","nivel2":"MÁLAGA","nivel3":"AYUNTAMIENTO DE MÁLAGA"},
			   "sede":null,"publicada":"2026-11-02","tipo":"Concurrencia competitiva - canónica","presupuesto":600000,
			   "beneficiarios":["PYME Y PERSONAS FÍSICAS QUE DESARROLLAN ACTIVIDAD ECONÓMICA"],"sectores":[],
			   "regiones":[{"codigo":"ES617","nombre":"Málaga"}],"finalidad":null,"inicio":"2026-11-03","fin":"2026-12-15",
			   "textoInicio":null,"textoFin":null,"bases":null,"documento":{"id":20,"nombre":"bases.pdf"},"docs":[20],
			   "enlace":"https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/L1","comprobada":"2026-11-09","cambios":[]}}""";

	@Test
	void loPublicadoPorLaRecogidaSeLeeYSaleEnteroConLoLeido() throws Exception {
		kafkaTemplate.send("convocatorias", "L1", PUBLICADA.formatted(UUID.randomUUID())).get();
		esperar(() -> jdbc.sql("SELECT count(*) FROM lecturas WHERE lectura IS NOT NULL").query(Integer.class).single() == 1);

		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
		mvc.perform(post("/api/v1/lecturas")).andExpect(jsonPath("$.leidas").value(1));

		JsonNode leida = leer();
		assertThat(leida.get("tipo").asString()).isEqualTo("convocatoria.leida");
		JsonNode c = leida.get("convocatoria");
		assertThat(c.get("codigo").asString()).isEqualTo("L1");
		// Lo de la recogida, tal cual
		assertThat(c.get("enlace").asString()).endsWith("/convocatorias/L1");
		assertThat(c.get("regiones").get(0).get("codigo").asString()).isEqualTo("ES617");
		// Y lo leído, con los nombres de la web
		assertThat(c.get("ia").get("resumen").asString()).isEqualTo("Dinero para modernizar el comercio.");
		assertThat(c.get("ia").get("plazoFin").asString()).isEqualTo("2026-12-15");
		assertThat(c.get("ia").get("condiciones").get("autonomos").asString()).isEqualTo("si");
		assertThat(c.get("iaModelo").asString()).isEqualTo("modelo-de-prueba");
	}

	private JsonNode leer() {
		Map<String, Object> config = Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		List<JsonNode> leidos = new ArrayList<>();
		try (var consumidor = new KafkaConsumer<String, String>(config)) {
			consumidor.subscribe(List.of("convocatorias.leidas"));
			Instant limite = Instant.now().plusSeconds(30);
			while (leidos.isEmpty() && Instant.now().isBefore(limite)) {
				for (ConsumerRecord<String, String> r : consumidor.poll(Duration.ofMillis(500))) leidos.add(json.readTree(r.value()));
			}
		}
		assertThat(leidos).as("eventos en convocatorias.leidas").isNotEmpty();
		return leidos.getFirst();
	}

	private static void esperar(BooleanSupplier condicion) throws InterruptedException {
		Instant limite = Instant.now().plusSeconds(30);
		while (!condicion.getAsBoolean()) {
			if (Instant.now().isAfter(limite)) throw new AssertionError("No ha pasado en 30 segundos");
			Thread.sleep(200);
		}
	}
}
