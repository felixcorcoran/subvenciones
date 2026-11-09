package es.felixcorcoran.subvenciones.avisos.dominio;

import java.time.LocalDate;

/**
 * Una convocatoria leída por la IA, tal como llega a los avisos. Se reparte una sola vez: la
 * primera vez que llega leída.
 *
 * @param categoria «ayuda», «premio» u «otro»: solo se avisa de las ayudas
 * @param cierre    la fecha de cierre que cuenta (la de las bases revisada, o la de la BDNS), si hay
 */
public record Novedad(String codigo, String titulo, String categoria, LocalDate cierre) {

	/** Se avisa de ella: es una ayuda y no ha cerrado */
	public boolean seAvisa(LocalDate hoy) {
		return "ayuda".equals(categoria) && (cierre == null || !cierre.isBefore(hoy));
	}
}
