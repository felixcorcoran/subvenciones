package es.felixcorcoran.subvenciones.recogida.dominio;

import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.EN_CASTELLANO;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.EN_CATALAN;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.conDocumentos;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.conFin;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.conTipo;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.ficha;
import static es.felixcorcoran.subvenciones.recogida.dominio.Fichas.sinBasesNiSectores;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.recogida.dominio.Ficha.DocumentoFicha;

class ReglasDeRecogidaTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 9);
	private static final LocalDate MANANA = HOY.plusDays(1);

	@Test
	void seDescartanLasDeConcesionDirectaYLasYaCerradas() {
		assertThat(ReglasDeRecogida.motivoDescarte(ficha("1"), HOY)).isEmpty();
		assertThat(ReglasDeRecogida.motivoDescarte(conTipo(ficha("1"), "Concesión directa - instrumental"), HOY))
				.contains("concesión directa");
		assertThat(ReglasDeRecogida.motivoDescarte(conFin(ficha("1"), HOY.minusDays(1)), HOY)).contains("cerrada");
		// El último día aún se puede pedir
		assertThat(ReglasDeRecogida.motivoDescarte(conFin(ficha("1"), HOY), HOY)).isEmpty();
	}

	@Test
	void elDocumentoDeLasBasesEsElDeCastellanoAunqueNoVayaPrimero() {
		Convocatoria c = ReglasDeRecogida.aConvocatoria(ficha("1"), HOY);
		assertThat(c.documento().id()).isEqualTo(EN_CASTELLANO.id());
		assertThat(c.docs()).containsExactly(10L, 20L);
		assertThat(c.enlace()).isEqualTo("https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/1");
		assertThat(c.comprobada()).isEqualTo(HOY);
	}

	@Test
	void unaFichaIgualNoCambiaNadaPeroQuedaComprobada() {
		Convocatoria g = ReglasDeRecogida.aConvocatoria(ficha("1"), HOY);
		var act = ReglasDeRecogida.actualizar(g, ficha("1"), MANANA);
		assertThat(act.campos()).isEmpty();
		assertThat(act.conservados()).isEmpty();
		assertThat(act.convocatoria().comprobada()).isEqualTo(MANANA);
		assertThat(act.convocatoria().cambios()).isEmpty();
	}

	@Test
	void loQueCambiaSeApuntaYLoQueDesapareceSeConserva() {
		Convocatoria g = ReglasDeRecogida.aConvocatoria(ficha("1"), HOY);
		Ficha hoy = conFin(sinBasesNiSectores(ficha("1")), LocalDate.of(2027, 1, 15));

		var act = ReglasDeRecogida.actualizar(g, hoy, MANANA);

		assertThat(act.campos()).containsExactly("fin");
		assertThat(act.conservados()).containsExactly("sectores", "bases");
		assertThat(act.convocatoria().fin()).isEqualTo(LocalDate.of(2027, 1, 15));
		assertThat(act.convocatoria().bases()).isEqualTo(g.bases());
		assertThat(act.convocatoria().sectores()).isEqualTo(g.sectores());
		assertThat(act.convocatoria().cambios()).hasSize(1);
		assertThat(act.convocatoria().cambios().getFirst().campos()).containsExactly("fin");
	}

	@Test
	void elDocumentoQueSeUsabaSeMantieneMientrasSigaEnLaFicha() {
		Convocatoria g = ReglasDeRecogida.aConvocatoria(conDocumentos(ficha("1"), List.of(EN_CATALAN)), HOY);
		assertThat(g.documento().id()).isEqualTo(EN_CATALAN.id());

		// Ahora también hay uno en castellano, pero el de antes sigue: no se cambia, y no se relee
		var act = ReglasDeRecogida.actualizar(g, ficha("1"), MANANA);
		assertThat(act.convocatoria().documento().id()).isEqualTo(EN_CATALAN.id());
		assertThat(act.campos()).containsExactly("documentos");

		// Si el de antes desaparece, se cambia al nuevo
		var otra = ReglasDeRecogida.actualizar(g, conDocumentos(ficha("1"), List.of(EN_CASTELLANO)), MANANA);
		assertThat(otra.convocatoria().documento().id()).isEqualTo(EN_CASTELLANO.id());
		assertThat(otra.campos()).containsExactly("documento", "documentos");
	}

	@Test
	void unaFichaSinDocumentosNoBorraElQueHabia() {
		Convocatoria g = ReglasDeRecogida.aConvocatoria(ficha("1"), HOY);
		var act = ReglasDeRecogida.actualizar(g, conDocumentos(ficha("1"), List.of()), MANANA);
		assertThat(act.convocatoria().documento()).isEqualTo(g.documento());
		assertThat(act.convocatoria().docs()).isEqualTo(g.docs());
		assertThat(act.conservados()).containsExactly("documento");
	}

	@Test
	void seGuardanSoloLosDiezUltimosCambios() {
		Convocatoria c = ReglasDeRecogida.aConvocatoria(ficha("1"), HOY);
		for (int i = 1; i <= 12; i++) {
			c = ReglasDeRecogida.actualizar(c, conFin(ficha("1"), HOY.plusDays(30 + i)), HOY.plusDays(i)).convocatoria();
		}
		assertThat(c.cambios()).hasSize(10);
		assertThat(c.cambios().getFirst().fecha()).isEqualTo(HOY.plusDays(3));
		assertThat(c.cambios().getLast().fecha()).isEqualTo(HOY.plusDays(12));
	}

	@Test
	void soloCierraLaFechaDeLaBdnsYSinFechaPasaAPorVerificarALos120Dias() {
		Convocatoria abierta = ReglasDeRecogida.aConvocatoria(ficha("1"), HOY);
		assertThat(ReglasDeRecogida.cerrada(abierta, HOY)).isFalse();
		assertThat(ReglasDeRecogida.cerrada(abierta, LocalDate.of(2026, 12, 16))).isTrue();

		Convocatoria sinFecha = ReglasDeRecogida.aConvocatoria(conFin(ficha("1"), null), HOY);
		assertThat(ReglasDeRecogida.cerrada(sinFecha, HOY.plusYears(2))).isFalse();
		LocalDate publicada = sinFecha.publicada();
		assertThat(ReglasDeRecogida.porVerificar(sinFecha, publicada.plusDays(120))).isFalse();
		assertThat(ReglasDeRecogida.porVerificar(sinFecha, publicada.plusDays(121))).isTrue();
	}

	@Test
	void seCompruebaUnaVezAlDiaYLasPorVerificarUnaVezALaSemana() {
		Convocatoria c = ReglasDeRecogida.aConvocatoria(ficha("1"), HOY);
		assertThat(ReglasDeRecogida.tocaComprobar(c, HOY)).isFalse();
		assertThat(ReglasDeRecogida.tocaComprobar(c, MANANA)).isTrue();

		LocalDate dentroDeUnAnio = HOY.plusYears(1);
		Convocatoria sinFecha = ReglasDeRecogida.aConvocatoria(conFin(ficha("1"), null), dentroDeUnAnio.minusDays(3));
		assertThat(ReglasDeRecogida.tocaComprobar(sinFecha, dentroDeUnAnio)).isFalse();
		assertThat(ReglasDeRecogida.tocaComprobar(sinFecha, dentroDeUnAnio.plusDays(4))).isTrue();
	}

	@Test
	void sinDocumentoEnCastellanoSeUsaElPrimero() {
		var unico = new DocumentoFicha(7, "Extracto", "extracto.pdf");
		Convocatoria c = ReglasDeRecogida.aConvocatoria(conDocumentos(ficha("1"), List.of(unico)), HOY);
		assertThat(c.documento().id()).isEqualTo(7);
	}
}
