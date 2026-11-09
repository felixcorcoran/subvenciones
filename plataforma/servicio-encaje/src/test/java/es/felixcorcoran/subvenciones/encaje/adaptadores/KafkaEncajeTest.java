package es.felixcorcoran.subvenciones.encaje.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

/** La entrada por Kafka contra un Kafka y un PostgreSQL de verdad */
class KafkaEncajeTest extends PruebaConContenedores {

	private static final String TOPICO = "convocatorias.leidas";

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void limpiar() {
		jdbc.sql("DELETE FROM convocatorias").update();
		jdbc.sql("DELETE FROM eventos_procesados").update();
	}

	private static String evento(UUID id, String codigo, String titulo) {
		return """
				{"id":"%s","tipo":"convocatoria.leida","version":1,"ocurrido":"2026-11-09T07:30:00Z",
				 "convocatoria":{"codigo":"%s","titulo":"%s","regiones":[{"codigo":"ES61","nombre":"Andalucía"}],
				                 "fin":"2026-12-31","ia":{"condiciones":{"autonomos":"si"}}}}"""
				.formatted(id, codigo, titulo);
	}

	private void enviar(String clave, String valor) throws Exception {
		kafkaTemplate.send(TOPICO, clave, valor).get();
	}

	private String titulo(String codigo) {
		return jdbc.sql("SELECT documento->>'titulo' FROM convocatorias WHERE codigo = :c")
				.param("c", codigo).query(String.class).optional().orElse(null);
	}

	private static void esperar(BooleanSupplier condicion) throws InterruptedException {
		Instant limite = Instant.now().plusSeconds(30);
		while (!condicion.getAsBoolean()) {
			if (Instant.now().isAfter(limite)) throw new AssertionError("No ha pasado en 30 segundos");
			Thread.sleep(200);
		}
	}

	@Test
	void unaConvocatoriaLeidaQuedaGuardadaParaElEncaje() throws Exception {
		enviar("K1", evento(UUID.randomUUID(), "K1", "Bonos al comercio"));

		esperar(() -> "Bonos al comercio".equals(titulo("K1")));
	}

	@Test
	void elMismoEventoEntregadoDosVecesSoloSeAplicaUna() throws Exception {
		UUID id = UUID.randomUUID();
		enviar("K2", evento(id, "K2", "Primera entrega"));
		esperar(() -> "Primera entrega".equals(titulo("K2")));

		// Misma id (una reentrega), aunque traiga otro contenido: no se aplica
		enviar("K2", evento(id, "K2", "Reentrega"));
		// Y un evento nuevo detrás, en la misma partición: cuando llegue, la reentrega ya se procesó
		enviar("K2", evento(UUID.randomUUID(), "K2", "Releída"));

		esperar(() -> "Releída".equals(titulo("K2")));
		assertThat(jdbc.sql("SELECT count(*) FROM eventos_procesados").query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void unMensajeQueNoSePuedeAplicarVaALaColaDeErroresSinBloquearLosDemas() throws Exception {
		enviar("K3", "{esto no es json");
		enviar("K3", evento(UUID.randomUUID(), "K3", "Detrás del roto"));

		esperar(() -> "Detrás del roto".equals(titulo("K3")));
		List<ConsumerRecord<String, String>> errores = leerColaDeErrores();
		assertThat(errores).extracting(ConsumerRecord::value).contains("{esto no es json");
	}

	private List<ConsumerRecord<String, String>> leerColaDeErrores() {
		Map<String, Object> config = Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		try (var consumidor = new KafkaConsumer<String, String>(config)) {
			consumidor.subscribe(List.of(TOPICO + ".dlt"));
			List<ConsumerRecord<String, String>> leidos = new ArrayList<>();
			Instant limite = Instant.now().plusSeconds(30);
			while (leidos.isEmpty() && Instant.now().isBefore(limite)) {
				consumidor.poll(Duration.ofMillis(500)).forEach(leidos::add);
			}
			return leidos;
		}
	}
}
