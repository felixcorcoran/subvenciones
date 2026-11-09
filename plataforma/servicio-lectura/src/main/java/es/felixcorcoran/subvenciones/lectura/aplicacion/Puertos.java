package es.felixcorcoran.subvenciones.lectura.aplicacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import es.felixcorcoran.subvenciones.lectura.dominio.Analisis;
import es.felixcorcoran.subvenciones.lectura.dominio.Lectura;

/** Los puertos de la lectura: lo que necesita del mundo y no sabe cómo se hace */
public final class Puertos {

	private Puertos() {
	}

	/** Las convocatorias que conoce la lectura y cómo va cada una */
	public interface Lecturas {
		Optional<Lectura> buscar(String codigo);

		List<Lectura> todas();

		void guardar(Lectura lectura);
	}

	/** Los documentos de las bases, de la BDNS */
	public interface Documentos {
		/** @throws RuntimeException si la BDNS no lo da: es un fallo de consulta, no de la convocatoria */
		byte[] descargar(long id);
	}

	/** La IA que lee las bases */
	public interface LectorIa {

		record Resultado(Analisis analisis, String modelo) {
		}

		/** Si hay clave y modelos con los que leer */
		boolean disponible();

		/**
		 * @throws IaNoDisponible si ningún modelo puede responder ahora (cupo, saturación): lo
		 *                        pendiente sigue en la siguiente pasada
		 * @throws RuntimeException si falla con esta convocatoria: cuenta como intento
		 */
		Resultado leer(byte[] pdf, String contexto);
	}

	public static class IaNoDisponible extends RuntimeException {
		public IaNoDisponible(String motivo) {
			super(motivo);
		}
	}

	/** Outbox: se anota «convocatoria.leida» en la misma transacción que lo leído */
	public interface Eventos {
		void anotarLeida(String codigo);
	}

	/** Los eventos ya aplicados: una reentrega de Kafka no se aplica dos veces */
	public interface EventosProcesados {
		boolean apuntar(UUID id);
	}

	public interface Transacciones {
		void enUna(Runnable trabajo);
	}
}
