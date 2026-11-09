package es.felixcorcoran.subvenciones.recogida.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Codigo;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Organo;

/**
 * Lo que dice hoy la ficha de una convocatoria en la BDNS, ya limpio: textos sin espacios de más
 * (y null si están vacíos), fechas solo si son fechas y regiones separadas en código y nombre. El
 * adaptador de la BDNS solo construye fichas completas: una ficha a medias es un fallo de consulta.
 */
public record Ficha(
		String codigo,
		String titulo,
		Organo organo,
		String sede,
		LocalDate publicada,
		String tipo,
		BigDecimal presupuesto,
		List<String> beneficiarios,
		List<Codigo> sectores,
		List<Codigo> regiones,
		String finalidad,
		LocalDate inicio,
		LocalDate fin,
		String textoInicio,
		String textoFin,
		String bases,
		List<DocumentoFicha> documentos) {

	public Ficha {
		beneficiarios = List.copyOf(beneficiarios);
		sectores = List.copyOf(sectores);
		regiones = List.copyOf(regiones);
		documentos = List.copyOf(documentos);
	}

	/** Un documento colgado de la ficha (bases, extracto, corrección…) */
	public record DocumentoFicha(long id, String descripcion, String nombre) {
	}
}
