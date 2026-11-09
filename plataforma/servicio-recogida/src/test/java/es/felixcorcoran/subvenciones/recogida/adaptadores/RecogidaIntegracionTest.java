package es.felixcorcoran.subvenciones.recogida.adaptadores;

import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.conFin;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.ficha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.felixcorcoran.subvenciones.recogida.aplicacion.Bdns;
import es.felixcorcoran.subvenciones.recogida.dominio.Ficha;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El servicio entero con PostgreSQL y Kafka de verdad y una BDNS de mentira: lo guardado sale por
 * la outbox a Kafka, en orden y con el formato acordado.
 */
@SpringBootTest(properties = { "subvenciones.recogida.cron=-", "subvenciones.outbox.cada=200ms" })
@Testcontainers(disabledWithoutDocker = true)
class RecogidaIntegracionTest {

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

	/** La BDNS de la prueba: busca lo que haya en el mapa y da su ficha */
	static final Map<String, Ficha> FICHAS = new ConcurrentHashMap<>();

	@TestConfiguration
	static class BdnsDePrueba {
		@Bean
		@Primary
		Bdns bdnsDePrueba() {
			return new Bdns() {
				@Override
				public List<String> buscar(LocalDate desde, LocalDate hasta) {
					return List.copyOf(FICHAS.keySet());
				}

				@Override
				public ResultadoFicha ficha(String codigo) {
					Ficha f = FICHAS.get(codigo);
					return f == null ? new ResultadoFicha.Fallo("no responde") : new ResultadoFicha.Leida(f);
				}
			};
		}
	}

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private JsonMapper json;

	private MockMvc mvc;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
		FICHAS.clear();
		for (String tabla : List.of("convocatorias", "descartadas", "pasadas")) jdbc.sql("DELETE FROM " + tabla).update();
	}

	@Test
	void loRecogidoSalePorLaOutboxAKafkaEnOrden() throws Exception {
		LocalDate lejos = LocalDate.now().plusYears(1);
		FICHAS.put("A1", conFin(ficha("A1"), lejos));
		mvc.perform(post("/api/v1/recogidas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.anadidas").value(1));

		// Mañana cambia el plazo: la pasada la comprueba y publica el cambio
		jdbc.sql("UPDATE convocatorias SET documento = jsonb_set(documento, '{comprobada}', '\"2000-01-01\"')").update();
		FICHAS.put("A1", conFin(ficha("A1"), lejos.plusDays(10)));
		mvc.perform(post("/api/v1/recogidas")).andExpect(jsonPath("$.cambiadas").value(1));

		List<JsonNode> eventos = leer("A1", 2);
		assertThat(eventos).extracting(e -> e.get("tipo").asString())
				.containsExactly("convocatoria.publicada", "convocatoria.cambiada");
		JsonNode cambiada = eventos.get(1);
		assertThat(cambiada.get("version").asInt()).isEqualTo(1);
		assertThat(cambiada.get("campos").get(0).asString()).isEqualTo("fin");
		JsonNode convocatoria = cambiada.get("convocatoria");
		assertThat(convocatoria.get("codigo").asString()).isEqualTo("A1");
		assertThat(convocatoria.get("fin").asString()).isEqualTo(lejos.plusDays(10).toString());
		assertThat(convocatoria.get("regiones").get(0).get("codigo").asString()).isEqualTo("ES617");
		assertThat(convocatoria.get("documento").get("id").asLong()).isEqualTo(20);

		esperar(() -> jdbc.sql("SELECT count(*) FROM outbox WHERE publicado IS NULL").query(Integer.class).single() == 0);
	}

	@Test
	void lanzarUnaPasadaAManoEsSoloParaAdministracion() throws Exception {
		MockMvc seguro = MockMvcBuilders.webAppContextSetup(contexto)
				.apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
		seguro.perform(post("/api/v1/recogidas")).andExpect(status().isUnauthorized());
		seguro.perform(post("/api/v1/recogidas")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
				.andExpect(status().isForbidden());
	}

	/** Los eventos de una convocatoria en el tópico, en el orden en que llegaron */
	private List<JsonNode> leer(String clave, int cuantos) {
		Map<String, Object> config = Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		List<JsonNode> leidos = new ArrayList<>();
		try (var consumidor = new KafkaConsumer<String, String>(config)) {
			consumidor.subscribe(List.of("convocatorias"));
			Instant limite = Instant.now().plusSeconds(30);
			while (leidos.size() < cuantos && Instant.now().isBefore(limite)) {
				for (ConsumerRecord<String, String> r : consumidor.poll(Duration.ofMillis(500))) {
					if (clave.equals(r.key())) leidos.add(json.readTree(r.value()));
				}
			}
		}
		return leidos;
	}

	private static void esperar(java.util.function.BooleanSupplier condicion) throws InterruptedException {
		Instant limite = Instant.now().plusSeconds(30);
		while (!condicion.getAsBoolean()) {
			if (Instant.now().isAfter(limite)) throw new AssertionError("No ha pasado en 30 segundos");
			Thread.sleep(200);
		}
	}
}
