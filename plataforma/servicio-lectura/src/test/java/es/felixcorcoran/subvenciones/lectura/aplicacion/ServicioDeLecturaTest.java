package es.felixcorcoran.subvenciones.lectura.aplicacion;

import static es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos.analisis;
import static es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos.conDocumento;
import static es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos.convocatoria;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.IaNoDisponible;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.LectorIa;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Lecturas;
import es.felixcorcoran.subvenciones.lectura.aplicacion.ServicioDeLectura.Tipo;
import es.felixcorcoran.subvenciones.lectura.dominio.Convocatoria;
import es.felixcorcoran.subvenciones.lectura.dominio.Lectura;

/** La lectura con la IA, los documentos y el almacén en memoria */
class ServicioDeLecturaTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final LocalDate HOY = LocalDate.of(2026, 11, 9);

	private final Map<String, Lectura> guardadas = new TreeMap<>();
	private final Set<UUID> procesados = new HashSet<>();
	private final List<String> leidasPublicadas = new ArrayList<>();
	private final List<String> descargadas = new ArrayList<>();
	private boolean bdnsFalla;
	/** Lo que responde la IA para cada contexto */
	private Function<String, LectorIa.Resultado> respuesta = contexto -> new LectorIa.Resultado(analisis(), "gemini-3.8-flash");

	private final Lecturas lecturas = new Lecturas() {
		@Override
		public Optional<Lectura> buscar(String codigo) {
			return Optional.ofNullable(guardadas.get(codigo));
		}

		@Override
		public List<Lectura> todas() {
			return List.copyOf(guardadas.values());
		}

		@Override
		public void guardar(Lectura l) {
			guardadas.put(l.codigo(), l);
		}
	};

	private final LectorIa ia = new LectorIa() {
		@Override
		public boolean disponible() {
			return true;
		}

		@Override
		public Resultado leer(byte[] pdf, String contexto) {
			return respuesta.apply(contexto);
		}
	};

	private ServicioDeLectura servicio() {
		Clock reloj = Clock.fixed(HOY.atTime(9, 0).atZone(MADRID).toInstant(), MADRID);
		return new ServicioDeLectura(lecturas, id -> {
			if (bdnsFalla) throw new IllegalStateException("BDNS 503");
			descargadas.add(String.valueOf(id));
			return new byte[] { '%', 'P', 'D', 'F' };
		}, ia, leidasPublicadas::add, procesados::add, Runnable::run, reloj, 10);
	}

	private void publicada(Convocatoria c) {
		servicio().alRecibir(UUID.randomUUID(), Tipo.PUBLICADA, c, List.of());
	}

	@Test
	void unaConvocatoriaPublicadaSeLeeYSaleComoLeida() {
		publicada(convocatoria("1"));

		var r = servicio().leerPendientes();

		assertThat(r.leidas()).isEqualTo(1);
		assertThat(guardadas.get("1").ia()).isEqualTo(analisis());
		assertThat(guardadas.get("1").modelo()).isEqualTo("gemini-3.8-flash");
		assertThat(leidasPublicadas).containsExactly("1");
		assertThat(servicio().leerPendientes().pendientes()).isZero();
	}

	@Test
	void elMismoEventoDosVecesSoloSeAplicaUna() {
		UUID id = UUID.randomUUID();
		var s = servicio();
		assertThat(s.alRecibir(id, Tipo.PUBLICADA, convocatoria("1"), List.of())).isTrue();
		assertThat(s.alRecibir(id, Tipo.CAMBIADA, convocatoria("1"), List.of("documento"))).isFalse();
	}

	@Test
	void siCambiaUnaYaLeidaElEncajeRecibeLoNuevoAunqueNoHayaQueReleerla() {
		publicada(convocatoria("1"));
		servicio().leerPendientes();
		leidasPublicadas.clear();

		servicio().alRecibir(UUID.randomUUID(), Tipo.CAMBIADA, convocatoria("1"), List.of("presupuesto"));

		assertThat(leidasPublicadas).containsExactly("1");
		assertThat(guardadas.get("1").revisar()).isNull();
	}

	@Test
	void siLaIaNoEstaDisponibleSeParaYLoPendienteSigue() {
		publicada(convocatoria("1"));
		publicada(convocatoria("2"));
		respuesta = contexto -> {
			throw new IaNoDisponible("cupo agotado");
		};

		var r = servicio().leerPendientes();

		assertThat(r.leidas()).isZero();
		assertThat(r.fallidas()).isZero();
		assertThat(guardadas.get("1").intentos()).isZero();
		assertThat(leidasPublicadas).isEmpty();
	}

	@Test
	void sinDocumentoDeLaBdnsNoCuentaComoIntento() {
		publicada(convocatoria("1"));
		bdnsFalla = true;

		servicio().leerPendientes();

		assertThat(guardadas.get("1").intentos()).isZero();
		assertThat(guardadas.get("1").error()).isNull();
	}

	@Test
	void unaRespuestaAMediasONoPdfCuentaComoIntento() {
		publicada(convocatoria("1"));
		publicada(conDocumento(convocatoria("2"), new Convocatoria.Documento(5, "bases.docx")));
		respuesta = contexto -> new LectorIa.Resultado(null, "gemini-3.6-flash");

		var r = servicio().leerPendientes();

		assertThat(r.fallidas()).isEqualTo(2);
		assertThat(guardadas.get("1").error()).isEqualTo("respuesta incompleta de gemini-3.6-flash");
		assertThat(guardadas.get("2").error()).isEqualTo("no es un PDF (bases.docx)");
		assertThat(descargadas).containsExactly("20");
		assertThat(leidasPublicadas).isEmpty();
	}

	@Test
	void unaRelecturaQueFallaMantieneLoLeidoYNoPublica() {
		publicada(convocatoria("1"));
		servicio().leerPendientes();
		servicio().alRecibir(UUID.randomUUID(), Tipo.CAMBIADA, convocatoria("1"), List.of("documento"));
		leidasPublicadas.clear();
		respuesta = contexto -> {
			throw new IllegalStateException("Gemini 400");
		};

		servicio().leerPendientes();

		assertThat(guardadas.get("1").ia()).isEqualTo(analisis());
		assertThat(guardadas.get("1").intentos()).isEqualTo(1);
		assertThat(leidasPublicadas).isEmpty();
	}

	@Test
	void unaCerradaNoSeLee() {
		publicada(convocatoria("1"));
		servicio().alRecibir(UUID.randomUUID(), Tipo.CERRADA, convocatoria("1"), List.of());

		assertThat(servicio().leerPendientes().pendientes()).isZero();
	}
}
