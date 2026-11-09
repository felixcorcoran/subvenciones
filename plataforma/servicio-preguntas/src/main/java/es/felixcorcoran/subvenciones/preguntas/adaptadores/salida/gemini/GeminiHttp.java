package es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.gemini;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.IaNoDisponible;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Incrustador;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Redactor;
import es.felixcorcoran.subvenciones.preguntas.dominio.Citas;
import es.felixcorcoran.subvenciones.preguntas.dominio.Pasaje;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini en el nivel gratuito, para dos cosas:
 *
 * <ul>
 * <li><b>Vectores</b> (embeddings) de los pasajes y de la pregunta, con un modelo de embeddings,
 * de 100 en 100 por petición.</li>
 * <li><b>La respuesta</b>, con la misma cadena de modelos que la lectura: el cupo del día agotado o
 * un modelo retirado no se vuelven a probar hasta mañana, y uno saturado o que no responde descansa
 * un rato.</li>
 * </ul>
 *
 * Los cuerpos de las peticiones se arman en orden fijo (LinkedHashMap): la misma pregunta da
 * siempre la misma petición, y así la evaluación puede grabar y reproducir las respuestas.
 */
public class GeminiHttp implements Incrustador, Redactor {

	private static final Logger log = LoggerFactory.getLogger(GeminiHttp.class);
	private static final ZoneId CUPO = ZoneId.of("America/Los_Angeles");
	private static final Pattern POR_DIA = Pattern.compile("PerDay", Pattern.CASE_INSENSITIVE);
	private static final int POR_LOTE = 100;

	/**
	 * @param dimensiones las mismas que la columna vector de la base de datos
	 */
	public record Ajustes(String clave, List<String> modelos, String modeloVectores, int dimensiones, Duration pausa,
			Duration descanso) {
	}

	record Respuesta(int estado, String cuerpo) {
	}

	private final RestClient http;
	private final JsonMapper json;
	private final Ajustes ajustes;
	private final Clock reloj;
	private final Map<String, LocalDate> agotados = new HashMap<>();
	private final Map<String, Instant> descansando = new HashMap<>();
	private final Object esquema;
	private long ultima;

	public GeminiHttp(RestClient http, JsonMapper json, Ajustes ajustes, Clock reloj) {
		this.http = http;
		this.json = json;
		this.ajustes = ajustes;
		this.reloj = reloj;
		this.esquema = json.readValue(Instrucciones.ESQUEMA, LinkedHashMap.class);
	}

	@Override
	public List<float[]> pasajes(List<String> textos) {
		List<float[]> vectores = new ArrayList<>();
		for (int i = 0; i < textos.size(); i += POR_LOTE) {
			vectores.addAll(vectores(textos.subList(i, Math.min(textos.size(), i + POR_LOTE)), "RETRIEVAL_DOCUMENT"));
		}
		return vectores;
	}

	@Override
	public float[] pregunta(String texto) {
		return vectores(List.of(texto), "RETRIEVAL_QUERY").getFirst();
	}

	private synchronized List<float[]> vectores(List<String> textos, String tarea) {
		String modelo = "models/" + ajustes.modeloVectores();
		List<Object> peticiones = new ArrayList<>();
		for (String t : textos) {
			var p = new LinkedHashMap<String, Object>();
			p.put("model", modelo);
			p.put("content", Map.of("parts", List.of(Map.of("text", t))));
			p.put("taskType", tarea);
			p.put("outputDimensionality", ajustes.dimensiones());
			peticiones.add(p);
		}
		Respuesta r;
		try {
			r = pedir("/" + ajustes.modeloVectores() + ":batchEmbedContents", Map.of("requests", peticiones));
		} catch (ResourceAccessException e) {
			throw new IaNoDisponible(ajustes.modeloVectores() + ": sin respuesta");
		}
		if (r.estado() == 429 || r.estado() >= 500) {
			throw new IaNoDisponible(ajustes.modeloVectores() + ": " + (POR_DIA.matcher(r.cuerpo()).find() ? "cupo diario agotado" : "saturado"));
		}
		if (r.estado() != 200) throw new IllegalStateException("Gemini " + r.estado() + ": " + corto(r.cuerpo()));
		List<float[]> vectores = new ArrayList<>();
		for (JsonNode e : json.readTree(r.cuerpo()).path("embeddings")) {
			JsonNode valores = e.path("values");
			float[] v = new float[valores.size()];
			for (int i = 0; i < v.length; i++) v[i] = (float) valores.get(i).asDouble();
			vectores.add(v);
		}
		if (vectores.size() != textos.size()) throw new IllegalStateException("Gemini devolvió " + vectores.size() + " vectores para " + textos.size() + " textos");
		return vectores;
	}

	@Override
	public synchronized Citas.Propuesta responder(String titulo, String pregunta, List<Pasaje> contexto) {
		var cuerpo = new LinkedHashMap<String, Object>();
		cuerpo.put("systemInstruction", Map.of("parts", List.of(Map.of("text", Instrucciones.SISTEMA))));
		var mensaje = new LinkedHashMap<String, Object>();
		mensaje.put("role", "user");
		mensaje.put("parts", List.of(Map.of("text", Instrucciones.mensaje(titulo, pregunta, contexto))));
		cuerpo.put("contents", List.of(mensaje));
		var config = new LinkedHashMap<String, Object>();
		config.put("responseMimeType", "application/json");
		config.put("responseSchema", esquema);
		config.put("temperature", 0);
		cuerpo.put("generationConfig", config);

		List<String> motivos = new ArrayList<>();
		for (String modelo : ajustes.modelos()) {
			if (agotado(modelo) || descansando(modelo)) continue;
			Respuesta r;
			try {
				esperar(ultima + ajustes.pausa().toMillis() - System.currentTimeMillis());
				ultima = System.currentTimeMillis();
				r = pedir("/" + modelo + ":generateContent", cuerpo);
			} catch (ResourceAccessException e) {
				descansar(modelo, "sin respuesta");
				motivos.add(modelo + ": sin respuesta");
				continue;
			}
			if (r.estado() == 200) return propuesta(r.cuerpo(), modelo);
			if (r.estado() == 404 || (r.estado() == 429 && POR_DIA.matcher(r.cuerpo()).find())) {
				agotados.put(modelo, LocalDate.now(reloj.withZone(CUPO)));
				motivos.add(modelo + ": no disponible hoy");
				continue;
			}
			if (r.estado() == 429 || r.estado() >= 500) {
				descansar(modelo, "saturado (" + r.estado() + ")");
				motivos.add(modelo + ": saturado");
				continue;
			}
			throw new IllegalStateException("Gemini " + r.estado() + ": " + corto(r.cuerpo()));
		}
		throw new IaNoDisponible("Ningún modelo disponible (" + (motivos.isEmpty() ? "todos agotados o descansando" : String.join("; ", motivos)) + ")");
	}

	/** La respuesta de la IA, tal cual: la comprobación de las citas la hace el dominio */
	private Citas.Propuesta propuesta(String cuerpo, String modelo) {
		JsonNode partes = json.readTree(cuerpo).path("candidates").path(0).path("content").path("parts");
		StringBuilder texto = new StringBuilder();
		for (JsonNode p : partes) texto.append(p.path("text").asString(""));
		JsonNode r = json.readTree(texto.toString());
		List<Citas.CitaPropuesta> citas = new ArrayList<>();
		for (JsonNode c : r.path("citas")) citas.add(new Citas.CitaPropuesta(c.path("pasaje").asInt(0), c.path("cita").asString("")));
		return new Citas.Propuesta(r.path("consta").asBoolean(false), r.path("respuesta").asString(""), citas, modelo);
	}

	private Respuesta pedir(String ruta, Object cuerpo) {
		return http.post()
				.uri(ruta)
				.header("x-goog-api-key", ajustes.clave())
				.contentType(MediaType.APPLICATION_JSON)
				.body(json.writeValueAsString(cuerpo))
				.exchange((peticion, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
						new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private boolean agotado(String modelo) {
		LocalDate dia = agotados.get(modelo);
		return dia != null && dia.equals(LocalDate.now(reloj.withZone(CUPO)));
	}

	private boolean descansando(String modelo) {
		Instant hasta = descansando.get(modelo);
		return hasta != null && hasta.isAfter(reloj.instant());
	}

	private void descansar(String modelo, String motivo) {
		descansando.put(modelo, reloj.instant().plus(ajustes.descanso()));
		log.warn("Gemini: {} {}; descansa {} minutos", modelo, motivo, ajustes.descanso().toMinutes());
	}

	private static String corto(String s) {
		return s.substring(0, Math.min(300, s.length()));
	}

	private static void esperar(long ms) {
		if (ms <= 0) return;
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IaNoDisponible("interrumpido");
		}
	}
}
