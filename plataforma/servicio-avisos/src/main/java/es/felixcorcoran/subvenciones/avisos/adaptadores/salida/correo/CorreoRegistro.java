package es.felixcorcoran.subvenciones.avisos.adaptadores.salida.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo;

/**
 * Sin SMTP configurado, los correos no salen: solo se apunta el asunto en el log. Ni la dirección
 * ni el texto, que lleva el enlace secreto de la persona. Para verlos en local, Mailpit.
 */
public class CorreoRegistro implements Correo {

	private static final Logger log = LoggerFactory.getLogger(CorreoRegistro.class);

	@Override
	public void enviar(Mensaje m) {
		log.info("Correo sin enviar (no hay SMTP): «{}»", m.asunto());
	}
}
