package es.felixcorcoran.subvenciones.encaje.adaptadores.radar;

/**
 * Las respuestas del formulario tal como las manda la web (y como van en sus enlaces:
 * ?provincia=ES617&amp;forma=autonomo…).
 *
 * @param forma      autonomo, sociedad o emprender
 * @param empleados  el mínimo del tramo: 0, 1, 10, 50 o 250
 * @param antiguedad nada, menos1, 1a3 o mas3
 */
public record PerfilRadar(String provincia, String municipio, String forma, Integer empleados, String antiguedad,
		String sector) {
}
