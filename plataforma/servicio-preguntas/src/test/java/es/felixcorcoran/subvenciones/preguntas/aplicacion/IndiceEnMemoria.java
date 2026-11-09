package es.felixcorcoran.subvenciones.preguntas.aplicacion;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Documentos;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Pasajes;
import es.felixcorcoran.subvenciones.preguntas.dominio.Documento;
import es.felixcorcoran.subvenciones.preguntas.dominio.Pasaje;

/** Documentos y pasajes en memoria, con búsqueda por similitud del coseno */
public class IndiceEnMemoria implements Documentos, Pasajes {
	public final Map<String, Documento> documentos = new LinkedHashMap<>();
	public final Map<String, List<Pasaje>> pasajes = new LinkedHashMap<>();
	public final Map<String, List<float[]>> vectores = new LinkedHashMap<>();

	@Override
	public Optional<Documento> buscar(String codigo) {
		return Optional.ofNullable(documentos.get(codigo));
	}

	@Override
	public void guardar(Documento d) {
		documentos.put(d.codigo(), d);
	}

	@Override
	public List<Documento> pendientes(int maximo) {
		return documentos.values().stream().filter(d -> d.estado() == Documento.Estado.PENDIENTE).limit(maximo).toList();
	}

	@Override
	public void reemplazar(String codigo, List<Pasaje> p, List<float[]> v) {
		pasajes.put(codigo, List.copyOf(p));
		vectores.put(codigo, List.copyOf(v));
	}

	@Override
	public List<Pasaje> parecidos(String codigo, float[] q, int cuantos) {
		List<Pasaje> suyos = pasajes.getOrDefault(codigo, List.of());
		List<float[]> vs = vectores.getOrDefault(codigo, List.of());
		return IntStream.range(0, suyos.size()).boxed()
				.sorted(Comparator.comparingDouble(i -> -coseno(q, vs.get(i))))
				.limit(cuantos)
				.map(suyos::get)
				.toList();
	}

	static double coseno(float[] a, float[] b) {
		double p = 0, na = 0, nb = 0;
		for (int i = 0; i < a.length; i++) {
			p += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		return p / (Math.sqrt(na) * Math.sqrt(nb));
	}
}
