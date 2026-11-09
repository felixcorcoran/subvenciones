package es.felixcorcoran.subvenciones.encaje.dominio;

import java.util.List;

/** Si un negocio puede pedir una ayuda, siempre con sus motivos */
public record Encaje(Estado estado, List<Motivo> motivos) {

	public Encaje {
		motivos = List.copyOf(motivos);
	}

	/** En la web: «Puedes pedirla», «Revisa» y «No encaja» */
	public enum Estado {
		SI, REVISA, NO
	}

	public record Motivo(Tipo tipo, String texto) {
	}

	public enum Tipo {
		BIEN, REVISA, NO
	}
}
