package es.felixcorcoran.subvenciones.lectura.adaptadores.entrada.kafka;

import java.time.format.DateTimeParseException;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.outbox.OutboxPostgres;
import tools.jackson.core.JacksonException;

/**
 * Tópicos y errores, como en el servicio de encaje (docs/decisiones/0003): un fallo pasajero se
 * reintenta con espera creciente; lo que nunca se podrá aplicar va a convocatorias.lectura.dlt.
 */
@Configuration
class ConfiguracionKafka {

	static final String COLA_DE_ERRORES = "convocatorias.lectura.dlt";

	@Bean
	NewTopic convocatorias() {
		return TopicBuilder.name(ConvocatoriasListener.TOPICO).partitions(3).build();
	}

	@Bean
	NewTopic convocatoriasLeidas() {
		return TopicBuilder.name(OutboxPostgres.TOPICO).partitions(3).build();
	}

	@Bean
	NewTopic colaDeErrores() {
		return TopicBuilder.name(COLA_DE_ERRORES).partitions(1).build();
	}

	@Bean
	CommonErrorHandler erroresKafka(KafkaTemplate<?, ?> kafka) {
		var aColaDeErrores = new DeadLetterPublishingRecoverer(kafka,
				(mensaje, error) -> new TopicPartition(COLA_DE_ERRORES, -1));
		var reintentos = new ExponentialBackOff(500, 2);
		reintentos.setMaxElapsedTime(10_000);
		var errores = new DefaultErrorHandler(aColaDeErrores, reintentos);
		errores.addNotRetryableExceptions(ConvocatoriasListener.EventoNoValido.class, JacksonException.class,
				DateTimeParseException.class);
		return errores;
	}
}
