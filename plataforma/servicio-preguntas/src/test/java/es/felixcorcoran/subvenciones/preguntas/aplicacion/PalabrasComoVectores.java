package es.felixcorcoran.subvenciones.preguntas.aplicacion;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Incrustador;

/**
 * Vectores de prueba sin IA: cada palabra suma en una posición fija (por su hash). Dos textos con
 * palabras en común se parecen, así que la búsqueda se puede probar de verdad sin llamar a Gemini.
 */
public class PalabrasComoVectores implements Incrustador {

	public static final int DIMENSIONES = 768;

	@Override
	public List<float[]> pasajes(List<String> textos) {
		return textos.stream().map(PalabrasComoVectores::vector).toList();
	}

	@Override
	public float[] pregunta(String texto) {
		return vector(texto);
	}

	static float[] vector(String texto) {
		float[] v = new float[DIMENSIONES];
		String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
		for (String palabra : limpio.split("[^a-z0-9]+")) {
			if (palabra.length() < 4) continue; // sin artículos ni preposiciones
			v[Math.floorMod(palabra.hashCode(), DIMENSIONES)] += 1;
		}
		v[0] += 0.01f; // ningún vector a cero
		return v;
	}
}
