package es.felixcorcoran.subvenciones.encaje.adaptadores.entrada.kafka;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.ConvocatoriaRadar;

/**
 * Evento «convocatoria.leida»: la IA ha leído (o releído) las bases de una convocatoria. Va en el
 * tópico convocatorias.leidas con la clave = código de la convocatoria, para que todos los eventos
 * de una misma convocatoria caigan en la misma partición y lleguen en orden.
 *
 * <pre>
 * {"id":"…uuid…","tipo":"convocatoria.leida","version":1,"ocurrido":"2026-11-09T07:30:00Z",
 *  "convocatoria":{…mismo formato que datos/convocatorias.json…}}
 * </pre>
 *
 * @param id identifica la entrega: si llega dos veces, solo se aplica una
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ConvocatoriaLeida(UUID id, String tipo, Integer version, Instant ocurrido, ConvocatoriaRadar convocatoria) {

	static final String TIPO = "convocatoria.leida";
	static final int VERSION = 1;

	/** @throws EventoNoValido si no es un evento que este servicio sepa aplicar */
	void validar() {
		if (id == null) throw new EventoNoValido("falta el id");
		if (!TIPO.equals(tipo)) throw new EventoNoValido("tipo desconocido: " + tipo);
		if (version == null || version != VERSION) throw new EventoNoValido("versión no admitida: " + version);
		if (convocatoria == null || convocatoria.codigo() == null || convocatoria.codigo().isBlank()) {
			throw new EventoNoValido("falta la convocatoria o su código");
		}
	}

	/** Reintentar no lo arregla: va directo a la cola de errores */
	static class EventoNoValido extends RuntimeException {
		EventoNoValido(String motivo) {
			super("Evento no válido: " + motivo);
		}
	}
}
