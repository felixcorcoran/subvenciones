package es.felixcorcoran.subvenciones.preguntas.aplicacion;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Descargas;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Documentos;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.EventosProcesados;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.IaNoDisponible;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Incrustador;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.LectorPdf;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Pasajes;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Redactor;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Transacciones;
import es.felixcorcoran.subvenciones.preguntas.dominio.Citas;
import es.felixcorcoran.subvenciones.preguntas.dominio.Documento;
import es.felixcorcoran.subvenciones.preguntas.dominio.Pasaje;
import es.felixcorcoran.subvenciones.preguntas.dominio.Respuesta;
import es.felixcorcoran.subvenciones.preguntas.dominio.Troceador;

/**
 * «Pregunta a la convocatoria»: búsqueda por significado en las bases (RAG) y respuesta con citas
 * literales comprobadas, o «no consta».
 */
public class ServicioDePreguntas {

	private static final Logger log = Logger.getLogger(ServicioDePreguntas.class.getName());
	/** Pasajes que se le dan a la IA para responder */
	public static final int PASAJES = 6;
	static final int MAX_BYTES = 18 * 1024 * 1024;

	public static class NoEncontrada extends RuntimeException {
		public NoEncontrada(String codigo) {
			super("No conocemos la convocatoria " + codigo);
		}
	}

	/** No se puede preguntar a estas bases: no son un PDF con texto, o no se han podido descargar */
	public static class SinBases extends RuntimeException {
		public SinBases(String motivo) {
			super(motivo);
		}
	}

	public static class PreguntaNoValida extends RuntimeException {
		public PreguntaNoValida() {
			super("La pregunta tiene que tener entre 3 y 500 caracteres");
		}
	}

	public record Resumen(int pendientes, int indexados, int sinTexto, int fallidos) {
	}

	private final Documentos documentos;
	private final Pasajes pasajes;
	private final Descargas descargas;
	private final LectorPdf lector;
	private final Incrustador incrustador;
	private final Redactor redactor;
	private final EventosProcesados procesados;
	private final Transacciones transacciones;

	public ServicioDePreguntas(Documentos documentos, Pasajes pasajes, Descargas descargas, LectorPdf lector,
			Incrustador incrustador, Redactor redactor, EventosProcesados procesados, Transacciones transacciones) {
		this.documentos = documentos;
		this.pasajes = pasajes;
		this.descargas = descargas;
		this.lector = lector;
		this.incrustador = incrustador;
		this.redactor = redactor;
		this.procesados = procesados;
		this.transacciones = transacciones;
	}

	/** Una convocatoria leída (o releída) por Kafka. Quien llama abre la transacción */
	public boolean alRecibir(UUID idEvento, String codigo, String titulo, Long documentoId, String nombre) {
		if (!procesados.apuntar(idEvento)) return false;
		Documento d = documentos.buscar(codigo)
				.map(antes -> antes.conLlegada(titulo, documentoId, nombre))
				.orElseGet(() -> Documento.nuevo(codigo, titulo, documentoId, nombre));
		documentos.guardar(d);
		return true;
	}

	/** Indexa lo pendiente, como mucho {@code maximo}; si la IA no está disponible, para */
	public Resumen indexarPendientes(int maximo) {
		var pendientes = documentos.pendientes(maximo);
		int indexados = 0, sinTexto = 0, fallidos = 0;
		for (Documento d : pendientes) {
			try {
				Documento r = indexar(d);
				switch (r.estado()) {
					case INDEXADO -> indexados++;
					case SIN_TEXTO -> sinTexto++;
					default -> fallidos++;
				}
			} catch (IaNoDisponible e) {
				log.info("IA no disponible para indexar: " + e.getMessage());
				break;
			}
		}
		return new Resumen(pendientes.size(), indexados, sinTexto, fallidos);
	}

	public Respuesta preguntar(String codigo, String pregunta) {
		if (pregunta == null || pregunta.strip().length() < 3 || pregunta.length() > 500) throw new PreguntaNoValida();
		Documento d = documentos.buscar(codigo).orElseThrow(() -> new NoEncontrada(codigo));
		if (d.estado() == Documento.Estado.PENDIENTE) d = indexar(d);
		if (d.estado() != Documento.Estado.INDEXADO) {
			throw new SinBases(d.motivo() == null ? "Estas bases no se pueden consultar" : d.motivo());
		}
		List<Pasaje> contexto = pasajes.parecidos(codigo, incrustador.pregunta(pregunta.strip()), PASAJES);
		if (contexto.isEmpty()) return Respuesta.noConsta(0, null);
		Citas.Propuesta propuesta = redactor.responder(d.titulo(), pregunta.strip(), contexto);
		return Citas.comprobar(propuesta, contexto);
	}

	/** @throws IaNoDisponible si no se pueden calcular los vectores ahora: el documento sigue pendiente */
	Documento indexar(Documento d) {
		if (!d.esPdf()) return guardar(d.sinTexto("Las bases no están en PDF (" + d.nombre() + ")"));
		List<String> paginas;
		try {
			byte[] pdf = descargas.descargar(d.documentoId());
			if (pdf.length > MAX_BYTES) return guardar(d.sinTexto("El PDF de las bases es demasiado grande"));
			paginas = lector.paginas(pdf);
		} catch (RuntimeException e) {
			log.warning(d.codigo() + ": no se pudo descargar o leer el PDF (" + e.getMessage() + ")");
			return guardar(d.fallo("No se pudo descargar o leer el PDF de las bases"));
		}
		List<Pasaje> trozos = Troceador.trocear(d.codigo(), paginas);
		if (trozos.isEmpty()) return guardar(d.sinTexto("El PDF de las bases no tiene texto (puede ser un escaneo)"));
		List<float[]> vectores = incrustador.pasajes(trozos.stream().map(Pasaje::texto).toList());
		Documento indexado = d.indexado();
		transacciones.enUna(() -> {
			pasajes.reemplazar(d.codigo(), trozos, vectores);
			documentos.guardar(indexado);
		});
		return indexado;
	}

	private Documento guardar(Documento d) {
		documentos.guardar(d);
		return d;
	}
}
