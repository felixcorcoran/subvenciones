package es.felixcorcoran.subvenciones.lectura.dominio;

import java.time.LocalDate;

/**
 * Una convocatoria y cómo va su lectura.
 *
 * @param ia               lo leído, o null si aún no se ha leído bien
 * @param modelo           el modelo que la leyó
 * @param revisar          por qué hay que volver a leerla (documento nuevo, plazo cambiado); lo leído
 *                         antes se mantiene mientras tanto
 * @param intentos         intentos fallidos seguidos
 * @param intentadoEl      día del último intento fallido
 * @param error            por qué falló el último intento
 * @param cierreDescartado fecha de cierre que la IA sacó de las bases y no se usa, con el motivo
 * @param cerrada          la recogida la ha retirado: no se lee más
 */
public record Lectura(
		Convocatoria convocatoria,
		Analisis ia,
		String modelo,
		String revisar,
		int intentos,
		LocalDate intentadoEl,
		String error,
		CierreDescartado cierreDescartado,
		boolean cerrada) {

	public record CierreDescartado(String fecha, String motivo) {
	}

	public static Lectura nueva(Convocatoria c) {
		return new Lectura(c, null, null, null, 0, null, null, null, false);
	}

	public String codigo() {
		return convocatoria.codigo();
	}

	public Lectura con(Convocatoria c) {
		return new Lectura(c, ia, modelo, revisar, intentos, intentadoEl, error, cierreDescartado, cerrada);
	}

	Lectura conRevisar(String motivo, int intentos, LocalDate intentadoEl, String error) {
		return new Lectura(convocatoria, ia, modelo, motivo, intentos, intentadoEl, error, cierreDescartado, cerrada);
	}

	Lectura conIa(Analisis ia, String modelo, CierreDescartado descartado) {
		return new Lectura(convocatoria, ia, modelo, null, 0, null, null, descartado, cerrada);
	}

	Lectura conFallo(int intentos, LocalDate dia, String error) {
		return new Lectura(convocatoria, ia, modelo, revisar, intentos, dia, error, cierreDescartado, cerrada);
	}

	public Lectura cerrar() {
		return new Lectura(convocatoria, ia, modelo, revisar, intentos, intentadoEl, error, cierreDescartado, true);
	}
}
