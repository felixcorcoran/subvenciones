package es.felixcorcoran.subvenciones.encaje.adaptadores.entrada.kafka;

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

import tools.jackson.core.JacksonException;

/**
 * Tópicos y errores. Un fallo pasajero (la base de datos no responde) se reintenta con espera
 * creciente; un mensaje que nunca se podrá aplicar (JSON roto, evento desconocido, fecha mal
 * escrita) va directo a la cola de errores (convocatorias.leidas.dlt), con la cabecera de la
 * excepción, para revisarlo a mano sin bloquear la partición.
 */
@Configuration
class ConfiguracionKafka {

	static final String CONVOCATORIAS_LEIDAS = "convocatorias.leidas";
	static final String COLA_DE_ERRORES = CONVOCATORIAS_LEIDAS + ".dlt";

	@Bean
	NewTopic convocatoriasLeidas() {
		return TopicBuilder.name(CONVOCATORIAS_LEIDAS).partitions(3).build();
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
		errores.addNotRetryableExceptions(ConvocatoriaLeida.EventoNoValido.class, JacksonException.class,
				DateTimeParseException.class);
		return errores;
	}
}
