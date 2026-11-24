package es.felixcorcoran.subvenciones.observabilidad;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Cuenta cada mensaje que acaba en una cola de errores (DLT): {@code subvenciones.kafka.cola.errores},
 * con el tópico del que venía. Así se puede avisar en cuanto llega algo, sin ir a mirar la DLT.
 *
 * Se engancha a cualquier {@link DefaultErrorHandler} del servicio, sin que cada servicio tenga que
 * acordarse: el aviso «recuperado» de Spring Kafka llega justo después de mandarlo a la DLT.
 */
@AutoConfiguration
@ConditionalOnClass({ DefaultErrorHandler.class, MeterRegistry.class })
public class MetricasKafkaAutoConfiguration {

	public static final String COLA_DE_ERRORES = "subvenciones.kafka.cola.errores";

	@Bean
	static BeanPostProcessor contarColaDeErrores(ObjectProvider<MeterRegistry> registro) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String nombre) {
				if (bean instanceof DefaultErrorHandler errores) {
					errores.setRetryListeners(new Contador(registro));
				}
				return bean;
			}
		};
	}

	static final class Contador implements RetryListener {

		private final ObjectProvider<MeterRegistry> registro;

		Contador(ObjectProvider<MeterRegistry> registro) {
			this.registro = registro;
		}

		@Override
		public void failedDelivery(ConsumerRecord<?, ?> mensaje, Exception error, int intento) {
			// Los reintentos no cuentan: solo lo que acaba en la cola de errores
		}

		@Override
		public void recovered(ConsumerRecord<?, ?> mensaje, Exception error) {
			MeterRegistry r = registro.getIfAvailable();
			if (r == null) return;
			Counter.builder(COLA_DE_ERRORES)
					.description("Mensajes enviados a la cola de errores")
					.tag("topico", mensaje.topic())
					.register(r)
					.increment();
		}
	}
}
