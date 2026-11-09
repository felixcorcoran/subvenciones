package es.felixcorcoran.subvenciones.avisos.adaptadores.salida.correo;

import java.nio.charset.StandardCharsets;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo;
import jakarta.mail.MessagingException;

/**
 * Los correos por SMTP, en HTML y texto plano a la vez. En local, Mailpit (docker compose) los
 * recoge y los enseña en http://localhost:8025 sin mandarlos a nadie; en producción puede ser
 * cualquier SMTP, por ejemplo el de Resend.
 */
public class CorreoSmtp implements Correo {

	private final JavaMailSender smtp;
	private final String remitente;

	public CorreoSmtp(JavaMailSender smtp, String remitente) {
		this.smtp = smtp;
		this.remitente = remitente;
	}

	@Override
	public void enviar(Mensaje m) {
		try {
			var mensaje = smtp.createMimeMessage();
			var ayudante = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
			ayudante.setFrom(remitente);
			ayudante.setTo(m.para());
			ayudante.setSubject(m.asunto());
			ayudante.setText(m.texto(), m.html());
			for (var cabecera : m.cabeceras().entrySet()) mensaje.setHeader(cabecera.getKey(), cabecera.getValue());
			smtp.send(mensaje);
		} catch (MessagingException e) {
			throw new IllegalStateException("No se pudo preparar el correo", e);
		}
	}
}
