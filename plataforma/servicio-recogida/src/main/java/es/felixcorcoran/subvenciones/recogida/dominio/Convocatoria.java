package es.felixcorcoran.subvenciones.recogida.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Una convocatoria guardada por la recogida: lo que dice su ficha de la BDNS, el documento de las
 * bases que se eligió y lo que ha ido cambiando. Los nombres son los de datos/convocatorias.json de
 * la web, así que en los eventos viaja con el mismo formato (sin la parte de la IA, que la añade el
 * servicio de lectura).
 *
 * @param docs       ids de todos los documentos de la ficha, para notar si se añade o quita alguno
 * @param comprobada último día en que se leyó bien su ficha
 * @param cambios    lo que ha cambiado en la ficha desde que se guardó (los últimos)
 */
public record Convocatoria(
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
		Documento documento,
		List<Long> docs,
		String enlace,
		LocalDate comprobada,
		List<Cambio> cambios) {

	public Convocatoria {
		Objects.requireNonNull(codigo, "codigo");
		beneficiarios = List.copyOf(Objects.requireNonNullElse(beneficiarios, List.of()));
		sectores = List.copyOf(Objects.requireNonNullElse(sectores, List.of()));
		regiones = List.copyOf(Objects.requireNonNullElse(regiones, List.of()));
		docs = List.copyOf(Objects.requireNonNullElse(docs, List.of()));
		cambios = List.copyOf(Objects.requireNonNullElse(cambios, List.of()));
	}

	public record Organo(String nivel1, String nivel2, String nivel3) {
	}

	public record Codigo(String codigo, String nombre) {
	}

	/** El documento de las bases que lee la IA */
	public record Documento(long id, String nombre) {
	}

	public record Cambio(LocalDate fecha, List<String> campos) {
		public Cambio {
			campos = List.copyOf(campos);
		}
	}
}
