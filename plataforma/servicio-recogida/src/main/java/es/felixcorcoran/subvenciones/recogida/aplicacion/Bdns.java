package es.felixcorcoran.subvenciones.recogida.aplicacion;

import java.time.LocalDate;
import java.util.List;

import es.felixcorcoran.subvenciones.recogida.dominio.Ficha;

/** Puerto: la API pública de la BDNS */
public interface Bdns {

	/**
	 * Códigos de las convocatorias registradas entre dos fechas para empresas (pymes, autónomos y
	 * grandes empresas).
	 *
	 * @throws RuntimeException si la búsqueda falla: sin ella no hay pasada
	 */
	List<String> buscar(LocalDate desde, LocalDate hasta);

	/** La ficha de una convocatoria, o por qué no se ha podido leer entera */
	ResultadoFicha ficha(String codigo);

	sealed interface ResultadoFicha {
		record Leida(Ficha ficha) implements ResultadoFicha {
		}

		/** No llegó, o llegó incompleta: se trata igual, y la convocatoria no se toca */
		record Fallo(String motivo) implements ResultadoFicha {
		}
	}
}
