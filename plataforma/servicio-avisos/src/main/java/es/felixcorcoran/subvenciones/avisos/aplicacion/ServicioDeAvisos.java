package es.felixcorcoran.subvenciones.avisos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Avisos;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Encajes;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.EventosProcesados;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Novedades;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Pasadas;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Suscriptores;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Transacciones;
import es.felixcorcoran.subvenciones.avisos.dominio.Encaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Novedad;
import es.felixcorcoran.subvenciones.avisos.dominio.Suscriptor;

/**
 * La pasada diaria de avisos, como AvisoService de alertas:
 *
 * <ol>
 * <li>Borra las altas sin confirmar de hace más de 48 horas.</li>
 * <li>Reparte: cada convocatoria leída que ha llegado desde la última pasada, si es una ayuda y está
 * abierta, se compara con el negocio de cada persona; si encaja o hay que revisarla, queda un aviso
 * pendiente.</li>
 * <li>Envía un correo por persona con sus avisos pendientes, hasta el tope diario; lo que no quepa
 * sale al día siguiente. Antes se vuelve a comparar: el negocio o la convocatoria pueden haber
 * cambiado.</li>
 * </ol>
 *
 * El encaje no se repite aquí: lo calcula el servicio de encaje, una llamada por persona y pasada.
 */
public class ServicioDeAvisos {

	private static final Logger log = Logger.getLogger(ServicioDeAvisos.class.getName());
	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	/** Ayudas por correo; el resto, en el enlace al radar */
	static final int MAX_AYUDAS_POR_CORREO = 20;

	public record Resumen(long sinConfirmarBorradas, int nuevas, int avisos, int correos, int paraManana) {
	}

	private final Suscriptores suscriptores;
	private final Novedades novedades;
	private final Avisos avisos;
	private final Pasadas pasadas;
	private final Encajes encajes;
	private final Correo correo;
	private final Plantillas plantillas;
	private final ServicioDeSuscripciones suscripciones;
	private final EventosProcesados procesados;
	private final Transacciones transacciones;
	private final Clock reloj;
	private final int maxCorreosDia;
	private final ReentrantLock enMarcha = new ReentrantLock();

	public ServicioDeAvisos(Suscriptores suscriptores, Novedades novedades, Avisos avisos, Pasadas pasadas,
			Encajes encajes, Correo correo, Plantillas plantillas, ServicioDeSuscripciones suscripciones,
			EventosProcesados procesados, Transacciones transacciones, Clock reloj, int maxCorreosDia) {
		this.suscriptores = suscriptores;
		this.novedades = novedades;
		this.avisos = avisos;
		this.pasadas = pasadas;
		this.encajes = encajes;
		this.correo = correo;
		this.plantillas = plantillas;
		this.suscripciones = suscripciones;
		this.procesados = procesados;
		this.transacciones = transacciones;
		this.reloj = reloj;
		this.maxCorreosDia = maxCorreosDia;
	}

	/**
	 * Una convocatoria leída (o releída) que llega por Kafka. Quien llama abre la transacción.
	 *
	 * @return false si el evento estaba repetido
	 */
	public boolean alRecibir(UUID idEvento, Novedad novedad) {
		if (!procesados.apuntar(idEvento)) return false;
		novedades.guardar(novedad);
		return true;
	}

	/** @throws IllegalStateException si ya hay una pasada en curso */
	public Resumen pasada() {
		if (!enMarcha.tryLock()) throw new IllegalStateException("Ya hay una pasada de avisos en marcha");
		try {
			return hacerPasada();
		} finally {
			enMarcha.unlock();
		}
	}

	private Resumen hacerPasada() {
		Instant ahora = reloj.instant();
		LocalDate hoy = LocalDate.ofInstant(ahora, MADRID);
		long borradas = suscripciones.borrarSinConfirmar();
		Map<UUID, List<Encaje>> encajesDeHoy = new HashMap<>();

		// La primera vez, todo lo que ha llegado ya estaba publicado: no es nuevo para nadie
		List<Novedad> sinRepartir = novedades.sinRepartir();
		if (!pasadas.hayAlguna()) {
			novedades.marcarRepartidas(sinRepartir.stream().map(Novedad::codigo).toList());
			var r = new Resumen(borradas, 0, 0, 0, 0);
			pasadas.apuntar(ahora, r);
			log.info("Primera pasada de avisos: " + sinRepartir.size() + " convocatorias quedan como ya vistas");
			return r;
		}

		// 1. Repartir
		Set<String> nuevas = new HashSet<>();
		for (Novedad n : sinRepartir) if (n.seAvisa(hoy)) nuevas.add(n.codigo());
		int[] creados = { 0 };
		if (!nuevas.isEmpty()) {
			for (Suscriptor s : suscriptores.activos()) {
				List<Encaje> suyos = encajesDeHoy.computeIfAbsent(s.id(), id -> encajes.para(s.perfil()));
				transacciones.enUna(() -> {
					for (Encaje e : suyos) {
						if (nuevas.contains(e.codigo()) && e.seAvisa()) {
							avisos.crear(s.id(), e.codigo(), e.estado(), ahora);
							creados[0]++;
						}
					}
				});
			}
		}
		novedades.marcarRepartidas(sinRepartir.stream().map(Novedad::codigo).toList());

		// 2. Enviar
		Instant inicioDelDia = hoy.atStartOfDay(MADRID).toInstant();
		long cupo = maxCorreosDia - avisos.correosDesde(inicioDelDia);
		int enviados = 0, paraManana = 0;
		for (var pendiente : avisos.pendientes().entrySet()) {
			if (enviados >= cupo) {
				paraManana++;
				continue;
			}
			Suscriptor s = suscriptores.porId(pendiente.getKey()).orElse(null);
			if (s == null) continue;
			Set<String> codigos = new HashSet<>(pendiente.getValue());
			List<Encaje> ayudas = encajesDeHoy.computeIfAbsent(s.id(), id -> encajes.para(s.perfil())).stream()
					.filter(e -> codigos.contains(e.codigo()) && e.seAvisa())
					.sorted(Comparator.comparing(Encaje::estado))
					.toList();
			try {
				if (!ayudas.isEmpty()) {
					correo.enviar(plantillas.avisos(s.email(), s.perfil(), s.token(),
							ayudas.subList(0, Math.min(ayudas.size(), MAX_AYUDAS_POR_CORREO)), ayudas.size()));
					enviados++;
				}
				transacciones.enUna(() -> {
					avisos.marcarEnviados(s.id(), ahora);
					suscriptores.guardar(s.conAviso(ahora));
				});
			} catch (RuntimeException e) {
				// Se reintenta en la próxima pasada
				log.warning("No se pudo enviar el correo de avisos a un suscriptor: " + e.getMessage());
			}
		}

		var r = new Resumen(borradas, nuevas.size(), creados[0], enviados, paraManana);
		pasadas.apuntar(ahora, r);
		log.info("Pasada de avisos: " + r);
		return r;
	}
}
