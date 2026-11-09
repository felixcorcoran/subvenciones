package es.felixcorcoran.subvenciones.avisos.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import es.felixcorcoran.subvenciones.avisos.dominio.Encaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Novedad;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;
import es.felixcorcoran.subvenciones.avisos.dominio.Suscriptor;

/** Los puertos de los avisos */
public final class Puertos {

	private Puertos() {
	}

	public interface Suscriptores {
		Optional<Suscriptor> porEmail(String email);

		Optional<Suscriptor> porToken(String token);

		Optional<Suscriptor> porId(UUID id);

		List<Suscriptor> activos();

		void guardar(Suscriptor s);

		/** La baja borra la dirección y sus avisos: no queda nada */
		void borrar(UUID id);

		long borrarPendientesCreadosAntesDe(Instant limite);
	}

	/** Las convocatorias leídas que han llegado y si ya se han repartido */
	public interface Novedades {
		/** La guarda si no estaba; si ya estaba, actualiza sus datos sin tocar si se repartió */
		void guardar(Novedad n);

		List<Novedad> sinRepartir();

		void marcarRepartidas(List<String> codigos);
	}

	/** Cada ayuda que encaja con alguien, hasta que sale en su correo */
	public interface Avisos {
		/** Una ayuda solo se avisa una vez a cada persona: si ya estaba, no hace nada */
		void crear(UUID suscriptor, String codigo, Encaje.Estado estado, Instant ahora);

		/** Los pendientes de envío, por persona */
		Map<UUID, List<String>> pendientes();

		void marcarEnviados(UUID suscriptor, Instant ahora);

		/** Personas que ya han recibido correo desde esa hora (uno por persona y pasada) */
		long correosDesde(Instant desde);
	}

	public interface Pasadas {
		boolean hayAlguna();

		void apuntar(Instant cuando, ServicioDeAvisos.Resumen resumen);
	}

	/** El servicio de encaje: las convocatorias abiertas con su encaje para un negocio */
	public interface Encajes {
		List<Encaje> para(Perfil perfil);
	}

	public interface Correo {
		/** @param cabeceras extra, como List-Unsubscribe para la baja con un clic */
		record Mensaje(String para, String asunto, String html, String texto, Map<String, String> cabeceras) {
		}

		void enviar(Mensaje mensaje);
	}

	/** Las provincias e islas válidas (códigos NUTS3) */
	public interface Provincias {
		Set<String> codigos();

		/** «Girona» para ES512; si no se conoce, el propio código */
		default String nombre(String codigo) {
			return codigo;
		}
	}

	public interface EventosProcesados {
		boolean apuntar(UUID id);
	}

	public interface Transacciones {
		void enUna(Runnable trabajo);
	}
}
