package es.felixcorcoran.subvenciones.preguntas.dominio;

import java.util.regex.Pattern;

/**
 * Las bases de una convocatoria que se pueden indexar, y cómo va su índice.
 *
 * @param documentoId id del documento en la BDNS (el mismo que lee la IA)
 */
public record Documento(String codigo, String titulo, Long documentoId, String nombre, Estado estado, int intentos,
		String motivo) {

	public enum Estado {
		/** Por indexar */
		PENDIENTE,
		INDEXADO,
		/** No se puede: no es un PDF o el PDF no tiene texto (escaneado) */
		SIN_TEXTO,
		/** Tres fallos seguidos al descargarlo o leerlo */
		FALLIDO
	}

	public static final int MAX_INTENTOS = 3;
	private static final Pattern PDF = Pattern.compile("\\.pdf$", Pattern.CASE_INSENSITIVE);

	public static Documento nuevo(String codigo, String titulo, Long documentoId, String nombre) {
		return new Documento(codigo, titulo, documentoId, nombre, Estado.PENDIENTE, 0, null);
	}

	public boolean esPdf() {
		return documentoId != null && nombre != null && PDF.matcher(nombre).find();
	}

	/** Si llega otra versión de la convocatoria: con otro documento, se vuelve a indexar */
	public Documento conLlegada(String titulo, Long documentoId, String nombre) {
		boolean otroDocumento = documentoId == null ? this.documentoId != null : !documentoId.equals(this.documentoId);
		if (!otroDocumento) return new Documento(codigo, titulo, this.documentoId, this.nombre, estado, intentos, motivo);
		return nuevo(codigo, titulo, documentoId, nombre);
	}

	public Documento indexado() {
		return new Documento(codigo, titulo, documentoId, nombre, Estado.INDEXADO, 0, null);
	}

	public Documento sinTexto(String motivo) {
		return new Documento(codigo, titulo, documentoId, nombre, Estado.SIN_TEXTO, intentos, motivo);
	}

	public Documento fallo(String motivo) {
		int n = intentos + 1;
		return new Documento(codigo, titulo, documentoId, nombre, n >= MAX_INTENTOS ? Estado.FALLIDO : Estado.PENDIENTE, n,
				motivo);
	}
}
