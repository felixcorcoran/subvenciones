package es.felixcorcoran.subvenciones.preguntas.dominio;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La regla que hace que no se invente nada: una cita solo vale si está, palabra por palabra, en el
 * pasaje que dice. Si después de comprobarlas no queda ninguna, la respuesta es «no consta»,
 * diga lo que diga la IA.
 */
public final class Citas {

	/** Más corta, una cita no demuestra nada («el plazo», «sí») */
	static final int MINIMO = 15;

	private Citas() {
	}

	/** Lo que propone la IA: su respuesta y, por cada afirmación, el pasaje (1, 2, 3…) y el texto citado */
	public record Propuesta(boolean consta, String respuesta, List<CitaPropuesta> citas, String modelo) {
		public Propuesta {
			citas = citas == null ? List.of() : List.copyOf(citas);
		}
	}

	public record CitaPropuesta(int pasaje, String cita) {
	}

	/**
	 * @param contexto los pasajes que se le dieron a la IA, en el orden en que se numeraron (1, 2, 3…)
	 */
	public static Respuesta comprobar(Propuesta p, List<Pasaje> contexto) {
		List<Respuesta.Cita> validas = new ArrayList<>();
		Set<String> vistas = new LinkedHashSet<>();
		int descartadas = 0;
		for (CitaPropuesta c : p.citas()) {
			String cita = limpiarCita(c.cita());
			boolean enRango = c.pasaje() >= 1 && c.pasaje() <= contexto.size();
			if (!enRango || cita.length() < MINIMO || !normalizar(contexto.get(c.pasaje() - 1).texto()).contains(normalizar(cita))) {
				descartadas++;
				continue;
			}
			if (vistas.add(normalizar(cita))) validas.add(new Respuesta.Cita(contexto.get(c.pasaje() - 1).pagina(), cita));
		}
		if (!p.consta() || validas.isEmpty() || p.respuesta() == null || p.respuesta().isBlank()) {
			return Respuesta.noConsta(descartadas, p.modelo());
		}
		return new Respuesta(p.respuesta().strip(), true, validas, descartadas, p.modelo());
	}

	/** Sin comillas ni puntos suspensivos alrededor, que la IA a veces añade */
	static String limpiarCita(String s) {
		if (s == null) return "";
		return s.strip().replaceAll("^[\"“”«»'‘’…. ]+|[\"“”«»'‘’…. ]+$", "").strip();
	}

	/**
	 * Para comparar sin que importen mayúsculas, espacios, saltos de línea, guiones de final de
	 * línea ni el tipo de comillas: lo que cambia al sacar el texto de un PDF, no las palabras.
	 */
	static String normalizar(String s) {
		String t = Normalizer.normalize(s, Normalizer.Form.NFC)
				.replace("­", "")
				.replaceAll("-\\s*\\n\\s*", "")
				.replaceAll("[“”«»]", "\"")
				.replaceAll("[‘’`´]", "'")
				.replaceAll("[–—]", "-")
				.replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT)
				.strip();
		return t;
	}
}
