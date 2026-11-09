package es.felixcorcoran.subvenciones.avisos.dominio;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * El negocio, con las mismas respuestas (y los mismos valores) que el formulario del radar: así
 * se le puede pasar tal cual al servicio de encaje y al enlace del radar.
 *
 * @param provincia  código NUTS3 de la provincia o isla
 * @param municipio  opcional
 * @param forma      autonomo, sociedad o emprender
 * @param empleados  el mínimo del tramo: 0, 1, 10, 50 o 250
 * @param antiguedad nada, menos1, 1a3 o mas3
 * @param sector     letra de la sección CNAE, o vacío si no lo sabe
 */
public record Perfil(String provincia, String municipio, String forma, int empleados, String antiguedad, String sector) {

	public static final Set<String> FORMAS = Set.of("autonomo", "sociedad", "emprender");
	public static final Set<Integer> TRAMOS = Set.of(0, 1, 10, 50, 250);
	public static final Set<String> ANTIGUEDADES = Set.of("nada", "menos1", "1a3", "mas3");

	public Perfil {
		municipio = Objects.requireNonNullElse(municipio, "").strip();
		sector = Objects.requireNonNullElse(sector, "").strip().toUpperCase(Locale.ROOT);
		// Quien aún no ha empezado no tiene plantilla ni antigüedad
		if ("emprender".equals(forma)) {
			empleados = 0;
			antiguedad = "nada";
		}
	}

	/** El primer dato que no es una respuesta del formulario, si lo hay */
	public Optional<String> datoNoValido(Set<String> provincias) {
		if (provincia == null || !provincias.contains(provincia)) return Optional.of("provincia");
		if (municipio.length() > 80) return Optional.of("municipio");
		if (!FORMAS.contains(forma)) return Optional.of("forma");
		if (!TRAMOS.contains(empleados)) return Optional.of("empleados");
		if (!ANTIGUEDADES.contains(antiguedad) || ("nada".equals(antiguedad) && !"emprender".equals(forma))) {
			return Optional.of("antiguedad");
		}
		if (!sector.isEmpty() && (sector.length() != 1 || sector.charAt(0) < 'A' || sector.charAt(0) > 'V')) {
			return Optional.of("sector");
		}
		return Optional.empty();
	}
}
