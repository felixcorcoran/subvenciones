package es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Provincias;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Las provincias e islas del mismo JSON que usan la web, alertas y el encaje (classpath:catalogo/) */
public class ProvinciasJson implements Provincias {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Provincia(String codigo, String nombre) {
	}

	private final Set<String> codigos;
	private final java.util.Map<String, String> nombres;

	public ProvinciasJson(JsonMapper json) {
		try (InputStream in = ProvinciasJson.class.getClassLoader().getResourceAsStream("catalogo/provincias.json")) {
			if (in == null) throw new IllegalStateException("Falta catalogo/provincias.json");
			List<Provincia> lista = json.readValue(in, new TypeReference<>() {
			});
			codigos = lista.stream().map(Provincia::codigo).collect(Collectors.toUnmodifiableSet());
			nombres = lista.stream().collect(Collectors.toUnmodifiableMap(Provincia::codigo, Provincia::nombre));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Set<String> codigos() {
		return codigos;
	}

	@Override
	public String nombre(String codigo) {
		return nombres.getOrDefault(codigo, codigo);
	}
}
