package es.felixcorcoran.subvenciones.recogida.aplicacion;

import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.conFin;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.conTipo;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.ficha;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria;
import es.felixcorcoran.subvenciones.recogida.dominio.Evento;
import es.felixcorcoran.subvenciones.recogida.dominio.Ficha;

/** La pasada entera con la BDNS, el almacén y los eventos en memoria */
class ServicioDeRecogidaTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	/** Una BDNS de mentira: lo que devuelve la búsqueda y la ficha de cada código (o un fallo) */
	static class BdnsFalsa implements Bdns {
		final List<String> encontradas = new ArrayList<>();
		final Map<String, Ficha> fichas = new HashMap<>();
		final List<String> pedidas = new ArrayList<>();
		LocalDate desdePedido;

		@Override
		public List<String> buscar(LocalDate desde, LocalDate hasta) {
			desdePedido = desde;
			return encontradas;
		}

		@Override
		public ResultadoFicha ficha(String codigo) {
			pedidas.add(codigo);
			Ficha f = fichas.get(codigo);
			return f == null ? new ResultadoFicha.Fallo("no responde") : new ResultadoFicha.Leida(f);
		}
	}

	static class AlmacenEnMemoria implements Almacen {
		final Map<String, Convocatoria> guardadas = new TreeMap<>();
		final Map<String, String> descartadas = new HashMap<>();
		Instant ultima;

		@Override
		public Optional<Convocatoria> guardada(String codigo) {
			return Optional.ofNullable(guardadas.get(codigo));
		}

		@Override
		public List<Convocatoria> guardadas() {
			return List.copyOf(guardadas.values());
		}

		@Override
		public void guardar(Convocatoria c) {
			guardadas.put(c.codigo(), c);
		}

		@Override
		public void retirar(String codigo) {
			guardadas.remove(codigo);
		}

		@Override
		public boolean descartada(String codigo) {
			return descartadas.containsKey(codigo);
		}

		@Override
		public void descartar(String codigo, String motivo, LocalDate fecha) {
			descartadas.put(codigo, motivo);
		}

		@Override
		public void olvidarDescartadasAntesDe(LocalDate fecha) {
		}

		@Override
		public Optional<Instant> ultimaPasada() {
			return Optional.ofNullable(ultima);
		}

		@Override
		public void apuntarPasada(Instant cuando, Resumen resumen) {
			ultima = cuando;
		}
	}

	private final BdnsFalsa bdns = new BdnsFalsa();
	private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
	private final List<Evento> eventos = new ArrayList<>();

	private ServicioDeRecogida servicio(LocalDate hoy) {
		Clock reloj = Clock.fixed(hoy.atTime(6, 30).atZone(MADRID).toInstant(), MADRID);
		return new ServicioDeRecogida(bdns, almacen, eventos::add, Runnable::run, reloj,
				new ServicioDeRecogida.Ajustes(14, 200));
	}

	private static final LocalDate HOY = LocalDate.of(2026, 11, 9);

	@Test
	void guardaLasNuevasQueSePuedenPedirYPublicaUnEventoPorCadaUna() {
		bdns.encontradas.addAll(List.of("1", "2", "3", "4"));
		bdns.fichas.put("1", ficha("1"));
		bdns.fichas.put("2", conTipo(ficha("2"), "Concesión directa - instrumental"));
		bdns.fichas.put("3", conFin(ficha("3"), HOY.minusDays(1)));
		// La 4 no tiene ficha: ni se guarda ni se descarta, se reintenta

		Resumen r = servicio(HOY).recoger();

		assertThat(almacen.guardadas).containsOnlyKeys("1");
		assertThat(almacen.descartadas).containsEntry("2", "concesión directa").containsEntry("3", "cerrada");
		assertThat(eventos).singleElement().isInstanceOf(Evento.Publicada.class);
		assertThat(r.anadidas()).isEqualTo(1);
		assertThat(r.descartadas()).isEqualTo(2);
		assertThat(r.nuevasSinFicha()).isEqualTo(1);
		assertThat(bdns.desdePedido).isEqualTo(HOY.minusDays(14));
	}

	@Test
	void unaNuevaSinFichaSeReintentaEnLaSiguientePasada() {
		bdns.encontradas.add("4");
		servicio(HOY).recoger();
		bdns.fichas.put("4", ficha("4"));

		servicio(HOY.plusDays(1)).recoger();

		assertThat(almacen.guardadas).containsOnlyKeys("4");
		// La segunda pasada empieza 4 días antes de la primera, por las que se registran con fecha atrasada
		assertThat(bdns.desdePedido).isEqualTo(HOY.minusDays(4));
	}

	@Test
	void alComprobarLasGuardadasSoloLoQueCambiaGeneraEvento() {
		bdns.encontradas.addAll(List.of("1", "2"));
		bdns.fichas.put("1", ficha("1"));
		bdns.fichas.put("2", ficha("2"));
		servicio(HOY).recoger();
		eventos.clear();

		bdns.encontradas.clear();
		bdns.fichas.put("2", conFin(ficha("2"), LocalDate.of(2027, 1, 31)));
		Resumen r = servicio(HOY.plusDays(1)).recoger();

		assertThat(r.comprobadas()).isEqualTo(2);
		assertThat(r.cambiadas()).isEqualTo(1);
		assertThat(eventos).singleElement().isInstanceOfSatisfying(Evento.Cambiada.class, e -> {
			assertThat(e.convocatoria().codigo()).isEqualTo("2");
			assertThat(e.campos()).containsExactly("fin");
		});
	}

	@Test
	void unFalloDeConsultaNoTocaLaConvocatoria() {
		bdns.encontradas.add("1");
		bdns.fichas.put("1", ficha("1"));
		servicio(HOY).recoger();
		Convocatoria antes = almacen.guardadas.get("1");
		eventos.clear();

		bdns.encontradas.clear();
		bdns.fichas.remove("1");
		Resumen r = servicio(HOY.plusDays(1)).recoger();

		assertThat(r.fallidas()).isEqualTo(1);
		assertThat(almacen.guardadas.get("1")).isEqualTo(antes);
		assertThat(eventos).isEmpty();
	}

	@Test
	void laQueCierraSegunLaBdnsSeRetiraConSuEventoYNoVuelve() {
		bdns.encontradas.add("1");
		bdns.fichas.put("1", ficha("1"));
		servicio(HOY).recoger();
		eventos.clear();

		LocalDate trasElCierre = LocalDate.of(2026, 12, 16);
		bdns.fichas.remove("1"); // aunque la ficha no responda, la fecha que ya tenía basta
		Resumen r = servicio(trasElCierre).recoger();

		assertThat(r.cerradas()).isEqualTo(1);
		assertThat(almacen.guardadas).isEmpty();
		assertThat(almacen.descartadas).containsEntry("1", "cerrada");
		assertThat(eventos).singleElement().isInstanceOf(Evento.Cerrada.class);

		// Si vuelve a salir en la búsqueda, no se pide su ficha
		bdns.encontradas.add("1");
		bdns.pedidas.clear();
		servicio(trasElCierre.plusDays(1)).recoger();
		assertThat(bdns.pedidas).isEmpty();
	}

	@Test
	void unaGuardadaYaComprobadaHoyNoSeVuelveAPedir() {
		bdns.encontradas.add("1");
		bdns.fichas.put("1", ficha("1"));
		servicio(HOY).recoger();
		bdns.pedidas.clear();

		servicio(HOY).recoger();

		assertThat(bdns.pedidas).isEmpty();
	}
}
