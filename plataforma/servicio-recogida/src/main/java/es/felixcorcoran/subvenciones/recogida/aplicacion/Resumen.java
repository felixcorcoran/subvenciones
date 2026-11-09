package es.felixcorcoran.subvenciones.recogida.aplicacion;

import java.time.LocalDate;

/**
 * Cómo ha ido una pasada. Una pasada sin nada nuevo también es correcta; lo que hay que vigilar son
 * las fichas que no se pueden leer.
 *
 * @param nuevasSinFicha nuevas cuya ficha no llegó entera: se reintentan en la siguiente pasada
 * @param fallidas       guardadas cuya ficha no se pudo leer: se quedan como estaban
 */
public record Resumen(
		LocalDate desde,
		int encontradas,
		int nuevas,
		int anadidas,
		int descartadas,
		int nuevasSinFicha,
		int comprobadas,
		int cambiadas,
		int fallidas,
		int cerradas,
		int guardadas) {
}
