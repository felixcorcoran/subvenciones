package es.felixcorcoran.subvenciones.lectura.dominio;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lo que la IA saca de las bases, con los mismos nombres y valores que el campo «ia» de
 * datos/convocatorias.json de la web (src/lib/tipos.ts). Solo lo que dice el documento: lo que no
 * consta queda como «no_consta» o null.
 *
 * @param categoria «ayuda», «premio» u «otro»
 * @param plazoFin  AAAA-MM-DD, solo si se puede saber con certeza y no contradice a la BDNS
 */
public record Analisis(
		String categoria,
		String resumen,
		String paraQuien,
		String importe,
		Double importeMaximo,
		String plazo,
		String plazoFin,
		List<String> requisitos,
		List<String> gastos,
		Condiciones condiciones,
		String dudas) {

	/** «si», «no» o «no_consta»; los números, null si las bases no los dicen */
	public record Condiciones(
			String autonomos,
			String sociedades,
			String emprendedores,
			Integer maxEmpleados,
			Integer antiguedadMinMeses,
			Integer antiguedadMaxMeses,
			String municipio) {
	}

	private static final Set<String> CATEGORIAS = Set.of("ayuda", "premio", "otro");
	private static final Set<String> SI_NO = Set.of("si", "no", "no_consta");
	private static final Pattern FECHA = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

	/**
	 * El esquema ya se le pide a la IA, pero se comprueba antes de guardar: no se fía de la
	 * respuesta. Una respuesta a medias no sustituye a nada (scripts/lectura.ts, analisisValido).
	 */
	public boolean valido() {
		Condiciones k = condiciones;
		return CATEGORIAS.contains(categoria)
				&& resumen != null && !resumen.isBlank()
				&& paraQuien != null && importe != null && plazo != null
				&& (importeMaximo == null || Double.isFinite(importeMaximo))
				&& requisitos != null && gastos != null
				&& k != null
				&& SI_NO.contains(k.autonomos()) && SI_NO.contains(k.sociedades()) && SI_NO.contains(k.emprendedores());
	}

	/** Con la fecha de cierre solo si es una fecha bien escrita */
	public Analisis conPlazoFinValido() {
		return plazoFin != null && FECHA.matcher(plazoFin).matches() ? this : conPlazoFin(null);
	}

	public Analisis conPlazoFin(String fecha) {
		return new Analisis(categoria, resumen, paraQuien, importe, importeMaximo, plazo, fecha, requisitos, gastos,
				condiciones, dudas);
	}
}
