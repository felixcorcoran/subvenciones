package es.felixcorcoran.subvenciones.preguntas.aplicacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import es.felixcorcoran.subvenciones.preguntas.dominio.Citas;
import es.felixcorcoran.subvenciones.preguntas.dominio.Documento;
import es.felixcorcoran.subvenciones.preguntas.dominio.Pasaje;

/** Los puertos de «pregunta a la convocatoria» */
public final class Puertos {

	private Puertos() {
	}

	/** Las bases que se conocen y cómo va su índice */
	public interface Documentos {
		Optional<Documento> buscar(String codigo);

		void guardar(Documento d);

		List<Documento> pendientes(int maximo);
	}

	/** Los pasajes indexados, con su vector */
	public interface Pasajes {
		/** Sustituye todos los pasajes de una convocatoria */
		void reemplazar(String codigo, List<Pasaje> pasajes, List<float[]> vectores);

		/** Los pasajes de esa convocatoria más parecidos a la pregunta, del más al menos parecido */
		List<Pasaje> parecidos(String codigo, float[] pregunta, int cuantos);
	}

	/** El PDF de las bases, de la BDNS */
	public interface Descargas {
		/** @throws RuntimeException si la BDNS no lo da */
		byte[] descargar(long documentoId);
	}

	/** El texto de cada página de un PDF; vacío si no tiene texto (escaneado) */
	public interface LectorPdf {
		List<String> paginas(byte[] pdf);
	}

	/** Los vectores (embeddings) con los que se busca por significado */
	public interface Incrustador {
		List<float[]> pasajes(List<String> textos);

		float[] pregunta(String texto);
	}

	/** La IA que redacta la respuesta a partir de los pasajes numerados */
	public interface Redactor {
		Citas.Propuesta responder(String titulo, String pregunta, List<Pasaje> contexto);
	}

	/** Ningún modelo puede responder ahora (cupo, saturación): se reintenta más tarde */
	public static class IaNoDisponible extends RuntimeException {
		public IaNoDisponible(String motivo) {
			super(motivo);
		}
	}

	public interface EventosProcesados {
		boolean apuntar(UUID id);
	}

	public interface Transacciones {
		void enUna(Runnable trabajo);
	}
}
