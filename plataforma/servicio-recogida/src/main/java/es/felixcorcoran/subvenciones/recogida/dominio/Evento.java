package es.felixcorcoran.subvenciones.recogida.dominio;

import java.util.List;

/**
 * Lo que la recogida cuenta al resto de servicios. Todos van en el tópico «convocatorias» con la
 * clave = código, así que los de una misma convocatoria llegan en orden.
 */
public sealed interface Evento {

	Convocatoria convocatoria();

	/** El nombre del tipo en el sobre del evento */
	String tipo();

	/** Una convocatoria nueva para empresas, abierta o sin fecha de cierre */
	record Publicada(Convocatoria convocatoria) implements Evento {
		@Override
		public String tipo() {
			return "convocatoria.publicada";
		}
	}

	/**
	 * Su ficha ha cambiado. Con los campos, quien escucha decide: por ejemplo, la lectura con IA
	 * vuelve a leer las bases si cambia el documento o el plazo.
	 */
	record Cambiada(Convocatoria convocatoria, List<String> campos) implements Evento {
		public Cambiada {
			campos = List.copyOf(campos);
		}

		@Override
		public String tipo() {
			return "convocatoria.cambiada";
		}
	}

	/** Ha pasado su fecha de cierre de la BDNS: se retira */
	record Cerrada(Convocatoria convocatoria) implements Evento {
		@Override
		public String tipo() {
			return "convocatoria.cerrada";
		}
	}
}
