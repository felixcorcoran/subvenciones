package es.felixcorcoran.subvenciones.lectura.adaptadores.entrada.kafka;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.persistencia.DatosOriginales;
import es.felixcorcoran.subvenciones.lectura.aplicacion.ServicioDeLectura;
import es.felixcorcoran.subvenciones.lectura.aplicacion.ServicioDeLectura.Tipo;
import es.felixcorcoran.subvenciones.lectura.dominio.Convocatoria;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrada por Kafka: lo que publica la recogida en el tópico «convocatorias». Una transacción por
 * mensaje: los datos, la lectura, el apunte del evento y, si toca, el «leída» de la outbox van
 * juntos; el offset se confirma después.
 */
@Component
class ConvocatoriasListener {

	static final String TOPICO = "convocatorias";

	private static final Map<String, Tipo> TIPOS = Map.of(
			"convocatoria.publicada", Tipo.PUBLICADA,
			"convocatoria.cambiada", Tipo.CAMBIADA,
			"convocatoria.cerrada", Tipo.CERRADA);

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Sobre(UUID id, String tipo, Integer version, JsonNode convocatoria, List<String> campos) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ConvocatoriaRecogida(String codigo, String titulo, Organo organo, LocalDate publicada, LocalDate inicio,
			LocalDate fin, String textoInicio, String textoFin, Documento documento) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Organo(String nivel1, String nivel2, String nivel3) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Documento(long id, String nombre) {
		}

		Convocatoria aDominio() {
			return new Convocatoria(codigo, titulo,
					organo == null ? null : new Convocatoria.Organo(organo.nivel1(), organo.nivel2(), organo.nivel3()),
					publicada, inicio, fin, textoInicio, textoFin,
					documento == null ? null : new Convocatoria.Documento(documento.id(), documento.nombre()));
		}
	}

	private final ServicioDeLectura servicio;
	private final DatosOriginales datos;
	private final JsonMapper json;

	ConvocatoriasListener(ServicioDeLectura servicio, DatosOriginales datos, JsonMapper json) {
		this.servicio = servicio;
		this.datos = datos;
		this.json = json;
	}

	@KafkaListener(topics = TOPICO)
	@Transactional
	public void recibir(ConsumerRecord<String, String> mensaje) {
		Sobre sobre = json.readValue(mensaje.value(), Sobre.class);
		Tipo tipo = TIPOS.get(sobre.tipo());
		if (sobre.id() == null || tipo == null || sobre.version() == null || sobre.version() != 1
				|| sobre.convocatoria() == null || sobre.convocatoria().isNull()) {
			throw new EventoNoValido("tipo " + sobre.tipo() + ", versión " + sobre.version());
		}
		var c = json.treeToValue(sobre.convocatoria(), ConvocatoriaRecogida.class);
		if (c.codigo() == null || c.publicada() == null) throw new EventoNoValido("falta el código o la fecha de registro");

		// Primero los datos: si el evento genera un «leída», sale ya con lo nuevo
		datos.guardar(c.codigo(), json.writeValueAsString(sobre.convocatoria()));
		servicio.alRecibir(sobre.id(), tipo, c.aDominio(), sobre.campos() == null ? List.of() : sobre.campos());
	}

	/** Reintentar no lo arregla: va directo a la cola de errores */
	static class EventoNoValido extends RuntimeException {
		EventoNoValido(String motivo) {
			super("Evento no válido: " + motivo);
		}
	}
}
