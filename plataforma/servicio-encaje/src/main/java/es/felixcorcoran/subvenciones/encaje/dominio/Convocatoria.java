package es.felixcorcoran.subvenciones.encaje.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Una convocatoria de la BDNS con lo que la IA ha leído en sus bases, reducida a lo que necesita
 * el encaje.
 *
 * @param fin      fecha de cierre de la ficha de la BDNS, si la hay
 * @param analisis lo que la IA ha leído en las bases, o null si aún no se han leído
 */
public record Convocatoria(
		String codigo,
		String titulo,
		Organo organo,
		List<String> beneficiarios,
		List<Codigo> sectores,
		List<Codigo> regiones,
		LocalDate fin,
		Analisis analisis) {

	public Convocatoria {
		Objects.requireNonNull(codigo, "codigo");
		organo = Objects.requireNonNullElse(organo, Organo.DESCONOCIDO);
		beneficiarios = List.copyOf(Objects.requireNonNullElse(beneficiarios, List.of()));
		sectores = List.copyOf(Objects.requireNonNullElse(sectores, List.of()));
		regiones = List.copyOf(Objects.requireNonNullElse(regiones, List.of()));
	}

	/** La fecha de cierre que cuenta: manda la de las bases, si la lectura la dejó como fiable */
	public LocalDate cierre() {
		return analisis != null && analisis.plazoFin() != null ? analisis.plazoFin() : fin;
	}

	public boolean abiertaEl(LocalDate dia) {
		LocalDate cierre = cierre();
		return cierre == null || !cierre.isBefore(dia);
	}

	/** El órgano convocante, en los tres niveles de la BDNS (LOCAL / MÁLAGA / AYUNTAMIENTO DE MÁLAGA) */
	public record Organo(String nivel1, String nivel2, String nivel3) {
		public static final Organo DESCONOCIDO = new Organo("", "", "");

		public Organo {
			nivel1 = Objects.requireNonNullElse(nivel1, "");
			nivel2 = Objects.requireNonNullElse(nivel2, "");
			nivel3 = Objects.requireNonNullElse(nivel3, "");
		}
	}

	/** Un código de la BDNS con su nombre: regiones NUTS (ES617) o sectores CNAE (56, I) */
	public record Codigo(String codigo, String nombre) {
	}

	/**
	 * Lo que la IA ha leído en las bases. Solo lo que dice el documento: lo demás no consta.
	 *
	 * @param dudas lo que la lectura no tiene claro; al usuario le llega como algo que revisar
	 */
	public record Analisis(String categoria, String resumen, String importe, String plazo, LocalDate plazoFin,
			Condiciones condiciones, String dudas) {

		public Analisis {
			condiciones = Objects.requireNonNullElse(condiciones, Condiciones.NO_CONSTAN);
		}
	}

	/** Condiciones de las bases; los números quedan en null si las bases no los dicen */
	public record Condiciones(
			Respuesta autonomos,
			Respuesta sociedades,
			Respuesta emprendedores,
			Integer maxEmpleados,
			Integer antiguedadMinMeses,
			Integer antiguedadMaxMeses,
			String municipio) {

		public static final Condiciones NO_CONSTAN = new Condiciones(null, null, null, null, null, null, null);

		public Condiciones {
			autonomos = Objects.requireNonNullElse(autonomos, Respuesta.NO_CONSTA);
			sociedades = Objects.requireNonNullElse(sociedades, Respuesta.NO_CONSTA);
			emprendedores = Objects.requireNonNullElse(emprendedores, Respuesta.NO_CONSTA);
		}
	}

	public enum Respuesta {
		SI, NO, NO_CONSTA
	}
}
