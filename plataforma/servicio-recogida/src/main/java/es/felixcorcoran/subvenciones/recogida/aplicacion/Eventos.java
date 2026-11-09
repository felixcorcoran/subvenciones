package es.felixcorcoran.subvenciones.recogida.aplicacion;

import es.felixcorcoran.subvenciones.recogida.dominio.Evento;

/**
 * Puerto: los eventos que hay que publicar. Se anotan en la misma transacción que el cambio
 * (outbox) y otro proceso los publica: si el servicio cae entre guardar y publicar, el evento no se
 * pierde.
 */
public interface Eventos {

	void anotar(Evento evento);
}
