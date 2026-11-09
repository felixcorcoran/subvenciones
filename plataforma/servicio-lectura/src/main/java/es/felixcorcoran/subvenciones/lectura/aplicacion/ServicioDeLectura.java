package es.felixcorcoran.subvenciones.lectura.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Documentos;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Eventos;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.EventosProcesados;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.IaNoDisponible;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.LectorIa;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Lecturas;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Transacciones;
import es.felixcorcoran.subvenciones.lectura.dominio.Convocatoria;
import es.felixcorcoran.subvenciones.lectura.dominio.Lectura;
import es.felixcorcoran.subvenciones.lectura.dominio.ReglasDeLectura;

/**
 * Casos de uso de la lectura:
 *
 * <ul>
 * <li>Aplicar lo que cuenta la recogida (convocatoria publicada, cambiada o cerrada).</li>
 * <li>Leer con IA lo pendiente, como scripts/analizar.ts: primero las que nunca han fallado; si
 * ningún modelo puede responder, lo pendiente sigue en la siguiente pasada.</li>
 * </ul>
 */
public class ServicioDeLectura {

	private static final Logger log = Logger.getLogger(ServicioDeLectura.class.getName());
	/** Más grande no lo acepta la IA */
	static final int MAX_BYTES = 18 * 1024 * 1024;

	public enum Tipo {
		PUBLICADA, CAMBIADA, CERRADA
	}

	public record Resumen(int pendientes, int leidas, int fallidas, boolean iaDisponible) {
	}

	private final Lecturas lecturas;
	private final Documentos documentos;
	private final LectorIa ia;
	private final Eventos eventos;
	private final EventosProcesados procesados;
	private final Transacciones transacciones;
	private final Clock reloj;
	private final int maxPorPasada;
	private final ReentrantLock enMarcha = new ReentrantLock();

	public ServicioDeLectura(Lecturas lecturas, Documentos documentos, LectorIa ia, Eventos eventos,
			EventosProcesados procesados, Transacciones transacciones, Clock reloj, int maxPorPasada) {
		this.lecturas = lecturas;
		this.documentos = documentos;
		this.ia = ia;
		this.eventos = eventos;
		this.procesados = procesados;
		this.transacciones = transacciones;
		this.reloj = reloj;
		this.maxPorPasada = maxPorPasada;
	}

	/**
	 * Aplica un evento de la recogida. Quien llama abre la transacción: el apunte del evento, la
	 * lectura y su evento «leída» van juntos.
	 *
	 * @return false si el evento estaba repetido
	 */
	public boolean alRecibir(UUID idEvento, Tipo tipo, Convocatoria c, List<String> campos) {
		if (!procesados.apuntar(idEvento)) return false;
		var guardada = lecturas.buscar(c.codigo());
		switch (tipo) {
			case PUBLICADA, CAMBIADA -> {
				if (guardada.isEmpty()) {
					lecturas.guardar(Lectura.nueva(c));
					return true;
				}
				Lectura l = ReglasDeLectura.alCambiar(guardada.get(), c, campos);
				lecturas.guardar(l);
				// Ya leída: el encaje tiene que saber lo que ha cambiado en la BDNS (plazo, zona…)
				if (l.ia() != null) eventos.anotarLeida(l.codigo());
			}
			case CERRADA -> guardada.ifPresent(l -> lecturas.guardar(l.cerrar().con(c)));
		}
		return true;
	}

	/** @throws IllegalStateException si ya hay una pasada en curso */
	public Resumen leerPendientes() {
		if (!enMarcha.tryLock()) throw new IllegalStateException("Ya hay una lectura en marcha");
		try {
			return pasada();
		} finally {
			enMarcha.unlock();
		}
	}

	private Resumen pasada() {
		LocalDate hoy = LocalDate.now(reloj);
		List<Lectura> pendientes = lecturas.todas().stream()
				.filter(l -> ReglasDeLectura.porLeer(l, hoy))
				.sorted(Comparator.comparingInt(Lectura::intentos))
				.toList();
		if (!ia.disponible()) {
			log.info("Sin clave de la IA: no se leen bases (" + pendientes.size() + " pendientes)");
			return new Resumen(pendientes.size(), 0, 0, false);
		}
		int leidas = 0, fallidas = 0;
		for (Lectura l : pendientes.stream().limit(maxPorPasada).toList()) {
			Convocatoria c = l.convocatoria();
			Lectura resultado;
			try {
				if (!ReglasDeLectura.esPdf(c.documento())) throw new IllegalArgumentException("no es un PDF (" + c.documento().nombre() + ")");
				byte[] pdf;
				try {
					pdf = documentos.descargar(c.documento().id());
				} catch (RuntimeException e) {
					// La BDNS no ha dado el documento: fallo de consulta, no cuenta como intento
					log.warning(c.codigo() + ": sin documento (" + e.getMessage() + "); se reintenta en la próxima pasada");
					continue;
				}
				if (pdf.length > MAX_BYTES) throw new IllegalArgumentException("documento demasiado grande (" + pdf.length / 1_000_000 + " MB)");
				var leido = ia.leer(pdf, ReglasDeLectura.contexto(c));
				if (leido.analisis() == null || !leido.analisis().valido()) {
					throw new IllegalStateException("respuesta incompleta de " + leido.modelo());
				}
				resultado = ReglasDeLectura.leida(l, leido.analisis(), leido.modelo());
				leidas++;
			} catch (IaNoDisponible e) {
				log.info("IA no disponible: " + e.getMessage() + ". Lo pendiente sigue en la próxima pasada");
				break;
			} catch (RuntimeException e) {
				resultado = ReglasDeLectura.fallo(l, e.getMessage(), hoy);
				fallidas++;
				log.warning(c.codigo() + ": " + resultado.error());
			}
			Lectura guardar = resultado;
			transacciones.enUna(() -> {
				lecturas.guardar(guardar);
				if (guardar.ia() != null && guardar.error() == null) eventos.anotarLeida(guardar.codigo());
			});
		}
		var r = new Resumen(pendientes.size(), leidas, fallidas, true);
		log.info("Lectura: " + r);
		return r;
	}
}
