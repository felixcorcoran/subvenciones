package es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.gemini;

import java.util.List;

import es.felixcorcoran.subvenciones.preguntas.dominio.Pasaje;

/** Lo que se le pide a la IA para responder, y el esquema de su respuesta */
final class Instrucciones {

	private Instrucciones() {
	}

	static final String SISTEMA = """
			Respondes preguntas sobre las bases de UNA convocatoria de ayudas públicas españolas, para el dueño de un pequeño negocio o un autónomo.

			Reglas:
			- Usa SOLO los pasajes numerados que te doy. No uses lo que sepas de otras convocatorias ni supongas lo habitual.
			- Si los pasajes no responden a la pregunta, pon consta=false, deja la respuesta vacía y no pongas citas.
			- Si responden, consta=true y la respuesta en español claro, de una a tres frases, sin jerga administrativa.
			- Cada dato de la respuesta tiene que tener su cita: copia el texto EXACTO del pasaje, sin cambiar, quitar ni añadir ninguna palabra, de entre 15 y 300 caracteres, e indica el número del pasaje del que sale.
			- Como mucho cuatro citas, las que hagan falta.""";

	static final String ESQUEMA = """
			{"type":"OBJECT","properties":{
			  "consta":{"type":"BOOLEAN"},
			  "respuesta":{"type":"STRING"},
			  "citas":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
			    "pasaje":{"type":"INTEGER"},
			    "cita":{"type":"STRING"}},"required":["pasaje","cita"]}}},
			 "required":["consta","respuesta","citas"]}""";

	/** La convocatoria, los pasajes numerados con su página, y la pregunta */
	static String mensaje(String titulo, String pregunta, List<Pasaje> contexto) {
		StringBuilder s = new StringBuilder("Convocatoria: ").append(titulo).append("\n\nPasajes de sus bases:\n");
		for (int i = 0; i < contexto.size(); i++) {
			s.append("\n[").append(i + 1).append("] (página ").append(contexto.get(i).pagina()).append(")\n")
					.append(contexto.get(i).texto()).append('\n');
		}
		return s.append("\nPregunta: ").append(pregunta).toString();
	}
}
