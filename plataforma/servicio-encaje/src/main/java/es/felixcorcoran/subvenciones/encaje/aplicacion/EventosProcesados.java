package es.felixcorcoran.subvenciones.encaje.aplicacion;

import java.util.UUID;

/**
 * Puerto: los eventos que ya se han aplicado. Kafka entrega «al menos una vez», así que el mismo
 * evento puede llegar dos veces; este registro hace que solo cuente la primera.
 */
public interface EventosProcesados {

	/** Lo apunta y devuelve true si es nuevo; false si ya estaba apuntado */
	boolean apuntar(UUID idEvento);
}
