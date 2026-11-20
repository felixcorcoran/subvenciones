package es.felixcorcoran.subvenciones.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MetricasKafkaTest {

	private final SimpleMeterRegistry registro = new SimpleMeterRegistry();

	private DefaultErrorHandler errores(List<ConsumerRecord<?, ?>> enviadosALaDlt) {
		var fabrica = new DefaultListableBeanFactory();
		fabrica.registerSingleton("registro", registro);
		var errores = new DefaultErrorHandler((mensaje, error) -> enviadosALaDlt.add(mensaje), new FixedBackOff(0, 1));
		var procesador = MetricasKafkaAutoConfiguration.contarColaDeErrores(fabrica.getBeanProvider(MeterRegistry.class));
		return (DefaultErrorHandler) procesador.postProcessAfterInitialization(errores, "erroresKafka");
	}

	private double contados(String topico) {
		var contador = registro.find(MetricasKafkaAutoConfiguration.COLA_DE_ERRORES).tag("topico", topico).counter();
		return contador == null ? 0 : contador.count();
	}

	@Test
	void cuentaLoQueAcabaEnLaColaDeErroresPorTopico() {
		List<ConsumerRecord<?, ?>> dlt = new ArrayList<>();
		var errores = errores(dlt);
		var mensaje = new ConsumerRecord<>("convocatorias.leidas", 0, 7L, "939979", "{roto");
		var error = new IllegalStateException("JSON roto");

		// Primer fallo: se reintenta; el segundo agota los reintentos y va a la DLT
		assertThat(errores.handleOne(error, mensaje, null, null)).isFalse();
		assertThat(contados("convocatorias.leidas")).isZero();
		assertThat(errores.handleOne(error, mensaje, null, null)).isTrue();

		assertThat(dlt).containsExactly(mensaje);
		assertThat(contados("convocatorias.leidas")).isEqualTo(1);
		assertThat(contados("convocatorias")).isZero();
	}
}
