package es.felixcorcoran.subvenciones.lectura.dominio;

import java.time.LocalDate;
import java.util.List;

/** Datos de prueba de la lectura */
public final class Ejemplos {

	private Ejemplos() {
	}

	public static Convocatoria convocatoria(String codigo) {
		return new Convocatoria(codigo, "Ayudas al comercio " + codigo,
				new Convocatoria.Organo("LOCAL", "MÁLAGA", "AYUNTAMIENTO DE MÁLAGA"), LocalDate.of(2026, 11, 2),
				LocalDate.of(2026, 11, 3), LocalDate.of(2026, 12, 15), null, null,
				new Convocatoria.Documento(20, "bases.pdf"));
	}

	public static Convocatoria conFechas(Convocatoria c, LocalDate inicio, LocalDate fin) {
		return new Convocatoria(c.codigo(), c.titulo(), c.organo(), c.publicada(), inicio, fin, c.textoInicio(),
				c.textoFin(), c.documento());
	}

	public static Convocatoria conDocumento(Convocatoria c, Convocatoria.Documento d) {
		return new Convocatoria(c.codigo(), c.titulo(), c.organo(), c.publicada(), c.inicio(), c.fin(), c.textoInicio(),
				c.textoFin(), d);
	}

	public static Analisis analisis(String plazo, String plazoFin, String dudas) {
		return new Analisis("ayuda", "Dinero para modernizar el comercio.", "Comercios de Málaga.",
				"Hasta 3.000 € por negocio", 3000.0, plazo, plazoFin, List.of("Estar en Málaga"), List.of("Obras"),
				new Analisis.Condiciones("si", "si", "no_consta", 10, null, null, null), dudas);
	}

	public static Analisis analisis() {
		return analisis("Hasta el 15 de diciembre de 2026", "2026-12-15", null);
	}
}
