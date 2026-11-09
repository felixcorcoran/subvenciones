package es.felixcorcoran.subvenciones.lectura.dominio;

import java.time.LocalDate;

/**
 * Lo que la lectura necesita de una convocatoria recogida: con qué documento leerla y los datos
 * de la BDNS que se comparan con lo leído. El resto de la convocatoria viaja tal cual, sin pasar
 * por aquí.
 */
public record Convocatoria(
		String codigo,
		String titulo,
		Organo organo,
		LocalDate publicada,
		LocalDate inicio,
		LocalDate fin,
		String textoInicio,
		String textoFin,
		Documento documento) {

	public record Organo(String nivel1, String nivel2, String nivel3) {
	}

	/** El documento de las bases que se lee */
	public record Documento(long id, String nombre) {
	}
}
