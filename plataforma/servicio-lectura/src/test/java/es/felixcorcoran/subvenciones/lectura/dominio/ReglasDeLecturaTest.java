package es.felixcorcoran.subvenciones.lectura.dominio;

import static es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos.analisis;
import static es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos.conDocumento;
import static es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos.conFechas;
import static es.felixcorcoran.subvenciones.lectura.dominio.Ejemplos.convocatoria;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReglasDeLecturaTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 9);

	private static Lectura leida(Convocatoria c, Analisis a) {
		return ReglasDeLectura.leida(Lectura.nueva(c), a, "gemini-3.8-flash");
	}

	@Test
	void seLeeLaQueTieneDocumentoYNoSeHaLeido() {
		assertThat(ReglasDeLectura.porLeer(Lectura.nueva(convocatoria("1")), HOY)).isTrue();
		assertThat(ReglasDeLectura.porLeer(Lectura.nueva(conDocumento(convocatoria("1"), null)), HOY)).isFalse();
		assertThat(ReglasDeLectura.porLeer(leida(convocatoria("1"), analisis()), HOY)).isFalse();
		assertThat(ReglasDeLectura.porLeer(Lectura.nueva(convocatoria("1")).cerrar(), HOY)).isFalse();
	}

	@Test
	void unaPorVerificarSinLeerNoSeLee() {
		Convocatoria sinFecha = conFechas(convocatoria("1"), null, null);
		LocalDate dentroDeCincoMeses = HOY.plusMonths(5);
		assertThat(ReglasDeLectura.porLeer(Lectura.nueva(sinFecha), HOY)).isTrue();
		assertThat(ReglasDeLectura.porLeer(Lectura.nueva(sinFecha), dentroDeCincoMeses)).isFalse();
	}

	@Test
	void trasTresFallosEsperaDosSemanas() {
		Lectura l = Lectura.nueva(convocatoria("1"));
		for (int i = 0; i < 3; i++) l = ReglasDeLectura.fallo(l, "respuesta incompleta", HOY);
		assertThat(l.intentos()).isEqualTo(3);
		assertThat(ReglasDeLectura.porLeer(l, HOY.plusDays(13))).isFalse();
		assertThat(ReglasDeLectura.porLeer(l, HOY.plusDays(14))).isTrue();
	}

	@Test
	void conFechaEnLaBdnsSoloValeLaMismaFecha() {
		Lectura coincide = leida(convocatoria("1"), analisis());
		assertThat(coincide.ia().plazoFin()).isEqualTo("2026-12-15");
		assertThat(coincide.cierreDescartado()).isNull();

		Lectura otra = leida(convocatoria("1"), analisis("Hasta el 20 de diciembre", "2026-12-20", null));
		assertThat(otra.ia().plazoFin()).isNull();
		assertThat(otra.cierreDescartado().motivo()).isEqualTo("no coincide con la de la BDNS (2026-12-15)");
	}

	@Test
	void sinFechaEnLaBdnsValeSiElPlazoLaNombraYNoHayDudas() {
		Convocatoria c = conFechas(convocatoria("1"), null, null);
		assertThat(leida(c, analisis("Hasta el 20 de diciembre de 2026", "2026-12-20", null)).ia().plazoFin())
				.isEqualTo("2026-12-20");
		assertThat(leida(c, analisis("Hasta el 20/12/2026", "2026-12-20", null)).ia().plazoFin()).isEqualTo("2026-12-20");
		assertThat(leida(c, analisis("Un mes desde la publicación", "2026-12-20", null)).cierreDescartado().motivo())
				.isEqualTo("el plazo leído en las bases no nombra esa fecha");
		assertThat(leida(c, analisis("Hasta el 20 de diciembre", "2026-12-20", "No está claro el plazo")).cierreDescartado()
				.motivo()).isEqualTo("la lectura de las bases tiene dudas sobre el plazo");
		assertThat(leida(c, analisis("Hasta el 20 de octubre", "2026-10-20", null)).cierreDescartado().motivo())
				.isEqualTo("es anterior al registro en la BDNS");
		assertThat(leida(c, analisis("pronto", "20/12/2026", null)).ia().plazoFin()).isNull();
	}

	@Test
	void unPlazoPrevioAlPeriodoDeLaBdnsEsElCierreParaElNegocio() {
		// Adhesión de comercios hasta el 20/11, y los bonos de la BDNS del 1/12 al 31/12
		Convocatoria c = conFechas(convocatoria("1"), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
		Lectura l = leida(c, analisis("Los comercios pueden adherirse hasta el 20 de noviembre", "2026-11-20", null));
		assertThat(l.ia().plazoFin()).isEqualTo("2026-11-20");
	}

	@Test
	void siCambiaElDocumentoOElPlazoSeReleeYMientrasSeMantieneLoLeido() {
		Lectura l = leida(convocatoria("1"), analisis());

		Lectura plazo = ReglasDeLectura.alCambiar(l, convocatoria("1"), List.of("textoFin"));
		assertThat(plazo.revisar()).isEqualTo("cambió el plazo");
		assertThat(plazo.ia()).isEqualTo(l.ia());
		assertThat(ReglasDeLectura.porLeer(plazo, HOY)).isTrue();

		Lectura doc = ReglasDeLectura.alCambiar(l, convocatoria("1"), List.of("documento", "fin"));
		assertThat(doc.revisar()).isEqualTo("documento nuevo");

		assertThat(ReglasDeLectura.alCambiar(l, convocatoria("1"), List.of("presupuesto")).revisar()).isNull();
	}

	@Test
	void siLaBdnsCambiaSuFechaSeVuelveARevisarLaDeLasBases() {
		Lectura l = leida(convocatoria("1"), analisis());
		Convocatoria ampliada = conFechas(convocatoria("1"), LocalDate.of(2026, 11, 3), LocalDate.of(2027, 1, 31));
		Lectura cambiada = ReglasDeLectura.alCambiar(l, ampliada, List.of("fin"));
		assertThat(cambiada.ia().plazoFin()).isNull();
		assertThat(cambiada.cierreDescartado().fecha()).isEqualTo("2026-12-15");
		assertThat(cambiada.revisar()).isEqualTo("cambió el plazo");
	}

	@Test
	void unDocumentoNuevoDaOtraOportunidadAunqueElAnteriorFallara() {
		Lectura l = Lectura.nueva(convocatoria("1"));
		for (int i = 0; i < 3; i++) l = ReglasDeLectura.fallo(l, "error", HOY);
		Lectura nueva = ReglasDeLectura.alCambiar(l, convocatoria("1"), List.of("documento"));
		assertThat(nueva.intentos()).isZero();
		assertThat(ReglasDeLectura.porLeer(nueva, HOY)).isTrue();
	}

	@Test
	void soloSeLeenPdfYElContextoLlevaLoQueDiceLaBdns() {
		assertThat(ReglasDeLectura.esPdf(new Convocatoria.Documento(1, "Bases.PDF"))).isTrue();
		assertThat(ReglasDeLectura.esPdf(new Convocatoria.Documento(1, "bases.docx"))).isFalse();
		assertThat(ReglasDeLectura.contexto(convocatoria("940577")))
				.contains("Convocatoria BDNS 940577: Ayudas al comercio 940577")
				.contains("Convoca: AYUNTAMIENTO DE MÁLAGA — MÁLAGA")
				.contains("Registrada en la BDNS el 2026-11-02.")
				.contains("Fin de solicitud según la BDNS: 2026-12-15.");
	}

	@Test
	void unaRespuestaAMediasNoEsValida() {
		assertThat(analisis().valido()).isTrue();
		var sinResumen = new Analisis("ayuda", " ", "x", "x", null, "x", null, List.of(), List.of(),
				analisis().condiciones(), null);
		assertThat(sinResumen.valido()).isFalse();
		var otraCategoria = new Analisis("subvención", "x", "x", "x", null, "x", null, List.of(), List.of(),
				analisis().condiciones(), null);
		assertThat(otraCategoria.valido()).isFalse();
	}
}
