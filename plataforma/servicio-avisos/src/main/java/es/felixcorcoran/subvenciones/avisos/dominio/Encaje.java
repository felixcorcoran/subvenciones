package es.felixcorcoran.subvenciones.avisos.dominio;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que responde el servicio de encaje para un negocio y una convocatoria abierta.
 *
 * @param estado SI, REVISA o NO
 */
public record Encaje(String codigo, String titulo, LocalDate cierre, Estado estado, List<Motivo> motivos) {

	public enum Estado {
		SI, REVISA, NO
	}

	public record Motivo(String tipo, String texto) {
	}

	public boolean seAvisa() {
		return estado != Estado.NO;
	}
}
