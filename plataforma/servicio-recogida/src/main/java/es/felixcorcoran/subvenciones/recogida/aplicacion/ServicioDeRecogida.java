package es.felixcorcoran.subvenciones.recogida.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import es.felixcorcoran.subvenciones.recogida.aplicacion.Bdns.ResultadoFicha;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria;
import es.felixcorcoran.subvenciones.recogida.dominio.Evento;
import es.felixcorcoran.subvenciones.recogida.dominio.ReglasDeRecogida;

/**
 * Una pasada de recogida, como scripts/recoger.ts:
 *
 * <ol>
 * <li>Busca las convocatorias para empresas registradas desde la última pasada (con unos días de
 * margen, porque algunas se registran con fecha atrasada) y guarda las nuevas que se pueden pedir.</li>
 * <li>Vuelve a pedir la ficha de las guardadas, primero las que hace más que no se comprueban, y
 * anota lo que cambia.</li>
 * <li>Retira las que han cerrado según la BDNS.</li>
 * </ol>
 *
 * Cada convocatoria va en su propia transacción, con su evento: un fallo a mitad de pasada no
 * deshace lo anterior, y lo guardado siempre tiene su evento.
 */
public class ServicioDeRecogida {

	private static final Logger log = Logger.getLogger(ServicioDeRecogida.class.getName());

	/** Configuración de la pasada */
	public record Ajustes(int diasAtrasLaPrimeraVez, int maxComprobar) {
	}

	private final Bdns bdns;
	private final Almacen almacen;
	private final Eventos eventos;
	private final Transacciones transacciones;
	private final Clock reloj;
	private final Ajustes ajustes;
	private final ReentrantLock enMarcha = new ReentrantLock();

	public ServicioDeRecogida(Bdns bdns, Almacen almacen, Eventos eventos, Transacciones transacciones, Clock reloj,
			Ajustes ajustes) {
		this.bdns = bdns;
		this.almacen = almacen;
		this.eventos = eventos;
		this.transacciones = transacciones;
		this.reloj = reloj;
		this.ajustes = ajustes;
	}

	/** @throws RecogidaEnMarcha si ya hay una pasada en curso */
	public Resumen recoger() {
		if (!enMarcha.tryLock()) throw new RecogidaEnMarcha();
		try {
			return pasada();
		} finally {
			enMarcha.unlock();
		}
	}

	private Resumen pasada() {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate desde = desde(hoy);

		// 1. Las nuevas
		var encontradas = new LinkedHashSet<>(bdns.buscar(desde, hoy));
		List<String> nuevas = encontradas.stream()
				.filter(codigo -> almacen.guardada(codigo).isEmpty() && !almacen.descartada(codigo))
				.toList();
		int anadidas = 0, descartadas = 0, nuevasSinFicha = 0;
		for (String codigo : nuevas) {
			switch (bdns.ficha(codigo)) {
				case ResultadoFicha.Fallo(String motivo) -> {
					nuevasSinFicha++;
					log.warning(codigo + ": sin ficha (" + motivo + "); se reintenta en la siguiente pasada");
				}
				case ResultadoFicha.Leida(var ficha) -> {
					var motivo = ReglasDeRecogida.motivoDescarte(ficha, hoy);
					if (motivo.isPresent()) {
						almacen.descartar(codigo, motivo.get(), hoy);
						descartadas++;
					} else {
						Convocatoria c = ReglasDeRecogida.aConvocatoria(ficha, hoy);
						transacciones.enUna(() -> {
							almacen.guardar(c);
							eventos.anotar(new Evento.Publicada(c));
						});
						anadidas++;
					}
				}
			}
		}

		// 2. Comprobar las guardadas
		List<Convocatoria> porComprobar = almacen.guardadas().stream()
				.filter(c -> ReglasDeRecogida.tocaComprobar(c, hoy))
				.sorted(Comparator.comparing(Convocatoria::comprobada, Comparator.nullsFirst(Comparator.naturalOrder())))
				.limit(ajustes.maxComprobar())
				.toList();
		int comprobadas = 0, cambiadas = 0, fallidas = 0;
		for (Convocatoria guardada : porComprobar) {
			switch (bdns.ficha(guardada.codigo())) {
				case ResultadoFicha.Fallo(String motivo) -> {
					fallidas++;
					log.warning(guardada.codigo() + ": no se pudo comprobar (" + motivo + "); se queda como estaba");
				}
				case ResultadoFicha.Leida(var ficha) -> {
					var act = ReglasDeRecogida.actualizar(guardada, ficha, hoy);
					transacciones.enUna(() -> {
						almacen.guardar(act.convocatoria());
						if (!act.campos().isEmpty()) eventos.anotar(new Evento.Cambiada(act.convocatoria(), act.campos()));
					});
					comprobadas++;
					if (!act.campos().isEmpty()) cambiadas++;
					if (!act.conservados().isEmpty()) {
						log.info(guardada.codigo() + ": la ficha ya no trae " + act.conservados() + "; se conserva lo anterior");
					}
				}
			}
		}

		// 3. Retirar las cerradas
		int cerradas = 0;
		for (Convocatoria c : almacen.guardadas()) {
			if (!ReglasDeRecogida.cerrada(c, hoy)) continue;
			transacciones.enUna(() -> {
				almacen.retirar(c.codigo());
				almacen.descartar(c.codigo(), "cerrada", hoy);
				eventos.anotar(new Evento.Cerrada(c));
			});
			cerradas++;
		}
		almacen.olvidarDescartadasAntesDe(hoy.minusDays(45));

		var resumen = new Resumen(desde, encontradas.size(), nuevas.size(), anadidas, descartadas, nuevasSinFicha,
				comprobadas, cambiadas, fallidas, cerradas, almacen.guardadas().size());
		almacen.apuntarPasada(reloj.instant(), resumen);
		log.info("Recogida: " + resumen);
		return resumen;
	}

	/** Desde la última pasada con 4 días de margen (como mucho 30 días atrás), o unos días la primera vez */
	private LocalDate desde(LocalDate hoy) {
		ZoneId zona = reloj.getZone();
		return almacen.ultimaPasada()
				.map(ultima -> LocalDate.ofInstant(ultima, zona).minusDays(4))
				.map(d -> d.isBefore(hoy.minusDays(30)) ? hoy.minusDays(30) : d)
				.orElse(hoy.minusDays(ajustes.diasAtrasLaPrimeraVez()));
	}

	public static class RecogidaEnMarcha extends RuntimeException {
		public RecogidaEnMarcha() {
			super("Ya hay una recogida en marcha");
		}
	}
}
