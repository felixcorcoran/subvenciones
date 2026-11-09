package es.felixcorcoran.subvenciones.preguntas.dominio;

import java.util.List;

/**
 * La respuesta a una pregunta sobre las bases: o lo que dicen, con sus citas literales, o «no
 * consta». Nunca una afirmación sin cita.
 *
 * @param consta              si las bases lo dicen
 * @param citasDescartadas    citas que propuso la IA y no aparecen tal cual en las bases
 */
public record Respuesta(String texto, boolean consta, List<Cita> citas, int citasDescartadas, String modelo) {

	public static final String NO_CONSTA = "Las bases no lo dicen, o no lo hemos encontrado en ellas. Consulta la convocatoria oficial.";

	public Respuesta {
		citas = List.copyOf(citas);
	}

	/** Un trozo literal de las bases y su página */
	public record Cita(int pagina, String texto) {
	}

	public static Respuesta noConsta(int descartadas, String modelo) {
		return new Respuesta(NO_CONSTA, false, List.of(), descartadas, modelo);
	}
}
