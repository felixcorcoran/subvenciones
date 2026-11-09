package es.felixcorcoran.subvenciones.encaje.adaptadores.entrada.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.TraductorRadar;
import es.felixcorcoran.subvenciones.encaje.aplicacion.ServicioDeEncaje;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrada por Kafka: cada convocatoria leída por la IA se guarda para el encaje.
 *
 * <p>Una transacción de base de datos por mensaje: el apunte del evento y la convocatoria se
 * confirman juntos, y el offset se confirma después. Si el servicio cae entre medias, el mensaje se
 * vuelve a entregar y el apunte hace que no se aplique dos veces.
 */
@Component
class ConvocatoriasLeidasListener {

	private static final Logger log = LoggerFactory.getLogger(ConvocatoriasLeidasListener.class);

	private final ServicioDeEncaje servicio;
	private final JsonMapper json;

	ConvocatoriasLeidasListener(ServicioDeEncaje servicio, JsonMapper json) {
		this.servicio = servicio;
		this.json = json;
	}

	@KafkaListener(topics = ConfiguracionKafka.CONVOCATORIAS_LEIDAS)
	@Transactional
	public void recibir(ConsumerRecord<String, String> mensaje) {
		ConvocatoriaLeida evento = json.readValue(mensaje.value(), ConvocatoriaLeida.class);
		evento.validar();
		boolean aplicado = servicio.registrarDesdeEvento(evento.id(), TraductorRadar.convocatoria(evento.convocatoria()));
		if (!aplicado) {
			log.info("Evento {} repetido (convocatoria {}): ya estaba aplicado", evento.id(), evento.convocatoria().codigo());
		}
	}
}
