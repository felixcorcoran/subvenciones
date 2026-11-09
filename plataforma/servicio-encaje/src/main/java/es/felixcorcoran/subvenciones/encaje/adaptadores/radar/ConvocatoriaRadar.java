package es.felixcorcoran.subvenciones.encaje.adaptadores.radar;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Una convocatoria tal como la publica la web en datos/convocatorias.json: fechas como texto y
 * condiciones como «si», «no» o «no_consta». Solo vive en el borde: {@link TraductorRadar} la pasa
 * al modelo del dominio, así el formato de la web no se cuela dentro.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConvocatoriaRadar(
		String codigo,
		String titulo,
		Organo organo,
		List<String> beneficiarios,
		List<Codigo> sectores,
		List<Codigo> regiones,
		String fin,
		Analisis ia) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Organo(String nivel1, String nivel2, String nivel3) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Codigo(String codigo, String nombre) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Analisis(String categoria, String resumen, String importe, String plazo, String plazoFin,
			Condiciones condiciones, String dudas) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Condiciones(String autonomos, String sociedades, String emprendedores, Integer maxEmpleados,
			Integer antiguedadMinMeses, Integer antiguedadMaxMeses, String municipio) {
	}
}
