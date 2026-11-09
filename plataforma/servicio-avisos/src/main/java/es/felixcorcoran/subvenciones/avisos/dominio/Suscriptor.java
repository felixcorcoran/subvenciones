package es.felixcorcoran.subvenciones.avisos.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Quien se apunta: su email, el perfil de su negocio y un token secreto, que es la única llave a
 * sus datos (va en los enlaces de sus correos). Nada más.
 *
 * @param consentimiento versión del aviso de privacidad que aceptó
 * @param ultimoAlta     último correo de alta enviado, para no repetirlo si se pulsa varias veces
 */
public record Suscriptor(
		UUID id,
		String email,
		Perfil perfil,
		String token,
		Estado estado,
		String consentimiento,
		Instant creado,
		Instant confirmado,
		Instant ultimoAlta,
		Instant ultimoAviso) {

	public enum Estado {
		PENDIENTE, ACTIVO
	}

	/** Entre dos correos de alta a la misma dirección, para que nadie la inunde */
	public static final Duration ENTRE_ALTAS = Duration.ofMinutes(10);
	/** Lo que se guarda una dirección sin confirmar */
	public static final Duration SIN_CONFIRMAR = Duration.ofHours(48);

	public static Suscriptor nuevo(String email, Perfil perfil, String token, String consentimiento, Instant ahora) {
		return new Suscriptor(UUID.randomUUID(), email, perfil, token, Estado.PENDIENTE, consentimiento, ahora, null, ahora,
				null);
	}

	public boolean activo() {
		return estado == Estado.ACTIVO;
	}

	/** Si ya se le puede volver a mandar el correo de alta */
	public boolean puedeRecibirAlta(Instant ahora) {
		return ultimoAlta == null || !ultimoAlta.plus(ENTRE_ALTAS).isAfter(ahora);
	}

	public Suscriptor confirmar(Instant ahora) {
		if (activo()) return this;
		return new Suscriptor(id, email, perfil, token, Estado.ACTIVO, consentimiento, creado, ahora, ultimoAlta, ultimoAviso);
	}

	public Suscriptor conPerfil(Perfil p) {
		return new Suscriptor(id, email, p, token, estado, consentimiento, creado, confirmado, ultimoAlta, ultimoAviso);
	}

	public Suscriptor conAlta(Instant ahora, String consentimiento) {
		return new Suscriptor(id, email, perfil, token, estado, consentimiento, creado, confirmado, ahora, ultimoAviso);
	}

	public Suscriptor conAviso(Instant ahora) {
		return new Suscriptor(id, email, perfil, token, estado, consentimiento, creado, confirmado, ultimoAlta, ahora);
	}
}
