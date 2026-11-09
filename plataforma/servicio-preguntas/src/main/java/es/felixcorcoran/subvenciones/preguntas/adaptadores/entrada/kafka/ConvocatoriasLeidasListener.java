package es.felixcorcoran.subvenciones.preguntas.adaptadores.entrada.kafka;

import java.util.UUID;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.backoff.ExponentialBackOff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Entrada por Kafka: cada convocatoria leída apunta sus bases para indexarlas */
@Component
class ConvocatoriasLeidasListener {

	static final String TOPICO = "convocatorias.leidas";
	static final String COLA_DE_ERRORES = "convocatorias.leidas.preguntas.dlt";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Sobre(UUID id, String tipo, Integer version, Convocatoria convocatoria) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Convocatoria(String codigo, String titulo, Documento documento) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Documento(Long id, String nombre) {
	}

	private final ServicioDePreguntas servicio;
	private final JsonMapper json;

	ConvocatoriasLeidasListener(ServicioDePreguntas servicio, JsonMapper json) {
		this.servicio = servicio;
		this.json = json;
	}

	@KafkaListener(topics = TOPICO)
	@Transactional
	public void recibir(ConsumerRecord<String, String> mensaje) {
		Sobre s = json.readValue(mensaje.value(), Sobre.class);
		if (s.id() == null || !"convocatoria.leida".equals(s.tipo()) || s.version() == null || s.version() != 1
				|| s.convocatoria() == null || s.convocatoria().codigo() == null) {
			throw new EventoNoValido("tipo " + s.tipo() + ", versión " + s.version());
		}
		var c = s.convocatoria();
		servicio.alRecibir(s.id(), c.codigo(), c.titulo() == null ? c.codigo() : c.titulo(),
				c.documento() == null ? null : c.documento().id(), c.documento() == null ? null : c.documento().nombre());
	}

	static class EventoNoValido extends RuntimeException {
		EventoNoValido(String motivo) {
			super("Evento no válido: " + motivo);
		}
	}

	/** Tópicos y errores, como el resto de servicios (docs/decisiones/0003) */
	@Configuration
	static class ConfiguracionKafka {

		@Bean
		NewTopic convocatoriasLeidas() {
			return TopicBuilder.name(TOPICO).partitions(3).build();
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
			errores.addNotRetryableExceptions(EventoNoValido.class, JacksonException.class);
			return errores;
		}
	}
}
