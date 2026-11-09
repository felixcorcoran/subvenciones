package es.felixcorcoran.subvenciones.encaje.adaptadores.salida.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.encaje.dominio.Catalogo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Provincias y secciones de la CNAE-2025 de los mismos JSON que usan la web y alertas (classpath:catalogo/) */
public class CatalogoJson implements Catalogo {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Provincia(String codigo, String nombre, String comunidad, String codigoComunidad) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Seccion(String letra, String nombre, int desde, int hasta) {
	}

	private static final Pattern LETRA = Pattern.compile("^[A-V]$");
	private static final Pattern DIVISION = Pattern.compile("^[0-9]+");

	private final List<Provincia> provincias;
	private final List<Seccion> secciones;
	private final Map<String, String> zonas = new HashMap<>();

	public CatalogoJson(JsonMapper json) {
		provincias = leer(json, "catalogo/provincias.json", new TypeReference<>() {
		});
		secciones = leer(json, "catalogo/sectores.json", new TypeReference<>() {
		});
		zonas.put("ES", "toda España");
		for (Provincia p : provincias) {
			zonas.put(p.codigo(), p.nombre());
			zonas.put(p.codigoComunidad(), p.comunidad());
		}
	}

	private static <T> T leer(JsonMapper json, String ruta, TypeReference<T> tipo) {
		try (InputStream in = CatalogoJson.class.getClassLoader().getResourceAsStream(ruta)) {
			if (in == null) throw new IllegalStateException("Falta " + ruta + " en el classpath");
			return json.readValue(in, tipo);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean esProvincia(String codigoNuts) {
		return provincias.stream().anyMatch(p -> p.codigo().equals(codigoNuts));
	}

	@Override
	public String nombreZona(String codigoNuts, String original) {
		return zonas.getOrDefault(codigoNuts, original);
	}

	@Override
	public String seccionDe(String codigoSector) {
		String c = codigoSector.strip().toUpperCase(Locale.ROOT);
		if (LETRA.matcher(c).matches()) return c;
		Matcher m = DIVISION.matcher(c);
		if (!m.find()) return null;
		int division = Integer.parseInt(m.group());
		return secciones.stream()
				.filter(s -> division >= s.desde() && division <= s.hasta())
				.map(Seccion::letra)
				.findFirst()
				.orElse(null);
	}

	@Override
	public String nombreSeccion(String letra) {
		return secciones.stream().filter(s -> s.letra().equals(letra)).map(Seccion::nombre).findFirst().orElse(letra);
	}
}
