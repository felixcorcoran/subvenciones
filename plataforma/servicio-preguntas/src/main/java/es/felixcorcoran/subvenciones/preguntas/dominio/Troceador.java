package es.felixcorcoran.subvenciones.preguntas.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parte el texto de las bases en pasajes que no cruzan de página (así cada cita sabe su página).
 * Se juntan párrafos hasta un tamaño que la búsqueda maneja bien; un párrafo demasiado largo se
 * parte por frases.
 */
public final class Troceador {

	/** Caracteres por pasaje, más o menos: lo bastante para un artículo corto de las bases */
	static final int MAXIMO = 1200;
	/** Una página con menos texto que esto (portadas, sellos) no aporta nada */
	static final int MINIMO_PAGINA = 40;

	private static final Pattern PARRAFOS = Pattern.compile("\\n\\s*\\n|\\n(?=\\s*(?:[A-ZÁÉÍÓÚÑ0-9][.)ºª]|Artículo|ARTÍCULO|Base|BASE|\\d+\\.))");
	private static final Pattern FRASES = Pattern.compile("(?<=[.;:])\\s+");

	private Troceador() {
	}

	/** @param paginas el texto de cada página, en orden */
	public static List<Pasaje> trocear(String codigo, List<String> paginas) {
		List<Pasaje> pasajes = new ArrayList<>();
		for (int p = 0; p < paginas.size(); p++) {
			String pagina = paginas.get(p) == null ? "" : paginas.get(p).strip();
			if (pagina.length() < MINIMO_PAGINA) continue;
			StringBuilder actual = new StringBuilder();
			for (String parrafo : trozos(pagina)) {
				if (actual.length() > 0 && actual.length() + parrafo.length() + 1 > MAXIMO) {
					pasajes.add(new Pasaje(codigo, pasajes.size(), p + 1, actual.toString()));
					actual.setLength(0);
				}
				if (actual.length() > 0) actual.append('\n');
				actual.append(parrafo);
			}
			if (actual.length() > 0) pasajes.add(new Pasaje(codigo, pasajes.size(), p + 1, actual.toString()));
		}
		return pasajes;
	}

	/** Los párrafos de una página, y los demasiado largos partidos por frases */
	private static List<String> trozos(String pagina) {
		List<String> trozos = new ArrayList<>();
		for (String parrafo : PARRAFOS.split(pagina)) {
			String limpio = parrafo.strip().replaceAll("[ \\t]+", " ");
			if (limpio.isEmpty()) continue;
			if (limpio.length() <= MAXIMO) {
				trozos.add(limpio);
				continue;
			}
			StringBuilder actual = new StringBuilder();
			for (String frase : FRASES.split(limpio)) {
				if (actual.length() > 0 && actual.length() + frase.length() + 1 > MAXIMO) {
					trozos.add(actual.toString());
					actual.setLength(0);
				}
				if (actual.length() > 0) actual.append(' ');
				// Una «frase» sin puntos más larga que el máximo se corta a lo bruto
				while (frase.length() > MAXIMO) {
					trozos.add(frase.substring(0, MAXIMO));
					frase = frase.substring(MAXIMO);
				}
				actual.append(frase);
			}
			if (actual.length() > 0) trozos.add(actual.toString());
		}
		return trozos;
	}
}
