package es.felixcorcoran.subvenciones.avisos.aplicacion;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Provincias;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Suscriptores;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Transacciones;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;
import es.felixcorcoran.subvenciones.avisos.dominio.Suscriptor;

/**
 * Alta con doble confirmación, cambios y baja, como alertas. La respuesta al apuntarse es siempre
 * la misma, esté o no el email apuntado: así no sirve para averiguar quién lo está.
 */
public class ServicioDeSuscripciones {

	/** Versión del aviso de privacidad que se acepta al apuntarse */
	public static final String PRIVACIDAD = "2026-10-26";

	private static final SecureRandom AZAR = new SecureRandom();

	public static class NoEncontrado extends RuntimeException {
		public NoEncontrado() {
			super("enlace");
		}
	}

	public static class PerfilNoValido extends RuntimeException {
		private final String dato;

		public PerfilNoValido(String dato) {
			super("El perfil no es válido: " + dato);
			this.dato = dato;
		}

		public String dato() {
			return dato;
		}
	}

	private final Suscriptores suscriptores;
	private final Provincias provincias;
	private final Plantillas plantillas;
	private final Correo correo;
	private final Transacciones transacciones;
	private final Clock reloj;

	public ServicioDeSuscripciones(Suscriptores suscriptores, Provincias provincias, Plantillas plantillas, Correo correo,
			Transacciones transacciones, Clock reloj) {
		this.suscriptores = suscriptores;
		this.provincias = provincias;
		this.plantillas = plantillas;
		this.correo = correo;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	public void apuntar(String email, Perfil perfil) {
		validar(perfil);
		String direccion = email.strip().toLowerCase(Locale.ROOT);
		Instant ahora = reloj.instant();
		transacciones.enUna(() -> {
			Optional<Suscriptor> existente = suscriptores.porEmail(direccion);
			if (existente.isEmpty()) {
				Suscriptor nuevo = Suscriptor.nuevo(direccion, perfil, token(), PRIVACIDAD, ahora);
				suscriptores.guardar(nuevo);
				correo.enviar(plantillas.confirmar(direccion, perfil, nuevo.token()));
				return;
			}
			Suscriptor s = existente.get();
			if (!s.puedeRecibirAlta(ahora)) return;
			if (!s.activo()) {
				// Aún sin confirmar: vale el último perfil, que es el que se confirmará
				suscriptores.guardar(s.conPerfil(perfil).conAlta(ahora, PRIVACIDAD));
				correo.enviar(plantillas.confirmar(direccion, perfil, s.token()));
			} else {
				// Ya apuntado: no se cambia nada sin el enlace de su correo
				suscriptores.guardar(s.conAlta(ahora, s.consentimiento()));
				correo.enviar(plantillas.yaApuntado(direccion, s.token()));
			}
		});
	}

	public Suscriptor confirmar(String token) {
		Suscriptor s = porToken(token).confirmar(reloj.instant());
		suscriptores.guardar(s);
		return s;
	}

	public Suscriptor ver(String token) {
		return porToken(token);
	}

	public Suscriptor cambiar(String token, Perfil perfil) {
		validar(perfil);
		Suscriptor s = porToken(token).conPerfil(perfil);
		suscriptores.guardar(s);
		return s;
	}

	/** La baja borra la dirección y sus avisos pendientes; un enlace que no existe no da error */
	public void borrar(String token) {
		suscriptores.porToken(token).ifPresent(s -> suscriptores.borrar(s.id()));
	}

	public long borrarSinConfirmar() {
		return suscriptores.borrarPendientesCreadosAntesDe(reloj.instant().minus(Suscriptor.SIN_CONFIRMAR));
	}

	private Suscriptor porToken(String token) {
		return suscriptores.porToken(token).orElseThrow(NoEncontrado::new);
	}

	private void validar(Perfil perfil) {
		if (perfil == null) throw new PerfilNoValido("perfil");
		perfil.datoNoValido(provincias.codigos()).ifPresent(dato -> {
			throw new PerfilNoValido(dato);
		});
	}

	/** 256 bits al azar: el enlace de cada persona es su única llave */
	static String token() {
		byte[] b = new byte[32];
		AZAR.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}
}
