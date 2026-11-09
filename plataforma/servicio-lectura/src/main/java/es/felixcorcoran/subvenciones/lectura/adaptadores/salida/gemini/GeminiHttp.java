package es.felixcorcoran.subvenciones.lectura.adaptadores.salida.gemini;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.IaNoDisponible;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.LectorIa;
import es.felixcorcoran.subvenciones.lectura.dominio.Analisis;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini (Google AI Studio) en su nivel gratuito, como scripts/gemini.ts: la clave tiene que ser
 * de un proyecto SIN facturación. El nivel gratuito tiene un cupo diario por modelo y los modelos
 * nuevos se saturan a menudo, así que se prueba una cadena de modelos:
 *
 * <ul>
 * <li>Cupo del día agotado (429 «PerDay») o modelo retirado (404): ese modelo no se vuelve a probar
 * hasta el día siguiente (el cupo se renueva a medianoche de California).</li>
 * <li>Límite por minuto (429): se espera y se repite una vez.</li>
 * <li>Saturado (5xx) o sin respuesta: se pasa al siguiente y ese modelo descansa un rato (30
 * minutos), para no esperar a que falle otra vez con cada convocatoria. Es una mejora sobre
 * scripts/gemini.ts: un modelo que no responde costaba hasta tres minutos por convocatoria.</li>
 * <li>Cualquier otro error es de esta convocatoria y cuenta como intento.</li>
 * </ul>
 */
public class GeminiHttp implements LectorIa {

	private static final Logger log = LoggerFactory.getLogger(GeminiHttp.class);
	private static final ZoneId CUPO = ZoneId.of("America/Los_Angeles");
	private static final Pattern POR_DIA = Pattern.compile("PerDay", Pattern.CASE_INSENSITIVE);

	/**
	 * Ajustes: modelos en orden de preferencia, pausa entre peticiones, espera tras el límite por
	 * minuto y descanso de un modelo saturado o que no responde
	 */
	public record Ajustes(String clave, List<String> modelos, Duration pausa, Duration esperaPorMinuto,
			Duration descanso) {
	}

	record Respuesta(int estado, String cuerpo) {
	}

	private final RestClient http;
	private final JsonMapper json;
	private final Ajustes ajustes;
	private final Clock reloj;
	private final Map<String, LocalDate> agotados = new HashMap<>();
	private final Map<String, java.time.Instant> descansando = new HashMap<>();
	private final Object schema;
	private long ultima;

	public GeminiHttp(RestClient http, JsonMapper json, Ajustes ajustes, Clock reloj) {
		this.http = http;
		this.json = json;
		this.ajustes = ajustes;
		this.reloj = reloj;
		this.schema = json.readValue(Instrucciones.ESQUEMA, Map.class);
	}

	@Override
	public boolean disponible() {
		return ajustes.clave() != null && !ajustes.clave().isBlank()
				&& ajustes.modelos().stream().anyMatch(m -> !agotado(m) && !descansando(m));
	}

	@Override
	public synchronized Resultado leer(byte[] pdf, String contexto) {
		Map<String, Object> cuerpo = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", Instrucciones.SISTEMA))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(
						Map.of("inlineData", Map.of("mimeType", "application/pdf", "data", Base64.getEncoder().encodeToString(pdf))),
						Map.of("text", contexto)))),
				"generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", schema));
		List<String> motivos = new ArrayList<>();
		for (String modelo : ajustes.modelos()) {
			if (agotado(modelo)) continue;
			if (descansando(modelo)) {
				motivos.add(modelo + ": descansando");
				continue;
			}
			for (int intento = 1; intento <= 2; intento++) {
				esperar(ultima + ajustes.pausa().toMillis() - System.currentTimeMillis());
				ultima = System.currentTimeMillis();
				Respuesta r;
				try {
					r = pedir(modelo, cuerpo);
				} catch (ResourceAccessException e) {
					// Sin respuesta o fallo de red: es cosa del modelo, no de la convocatoria
					descansar(modelo, "sin respuesta");
					motivos.add(modelo + ": sin respuesta");
					break;
				}
				if (r.estado() == 200) return new Resultado(analisis(r.cuerpo()), modelo);
				if (r.estado() == 429 && POR_DIA.matcher(r.cuerpo()).find()) {
					agotar(modelo);
					motivos.add(modelo + ": cupo diario agotado");
					break;
				}
				if (r.estado() == 429 && intento == 1) {
					esperar(ajustes.esperaPorMinuto().toMillis());
					continue;
				}
				if (r.estado() >= 500 || r.estado() == 429) {
					descansar(modelo, "saturado (" + r.estado() + ")");
					motivos.add(modelo + ": saturado");
					break;
				}
				if (r.estado() == 404) {
					agotar(modelo);
					motivos.add(modelo + ": no existe");
					break;
				}
				throw new IllegalStateException("Gemini " + r.estado() + ": " + r.cuerpo().substring(0, Math.min(400, r.cuerpo().length())));
			}
		}
		throw new IaNoDisponible("Ningún modelo disponible (" + (motivos.isEmpty() ? "todos agotados" : String.join("; ", motivos)) + ")");
	}

	private Respuesta pedir(String modelo, Map<String, Object> cuerpo) {
		return http.post()
				.uri("/{modelo}:generateContent", modelo)
				.header("x-goog-api-key", ajustes.clave())
				.contentType(MediaType.APPLICATION_JSON)
				.body(cuerpo)
				.exchange((peticion, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
						new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	/** El texto de la respuesta es el JSON del análisis; si no se puede leer, cuenta como intento */
	private Analisis analisis(String cuerpo) {
		JsonNode partes = json.readTree(cuerpo).path("candidates").path(0).path("content").path("parts");
		StringBuilder texto = new StringBuilder();
		for (JsonNode p : partes) texto.append(p.path("text").asString(""));
		return json.readValue(texto.toString(), Analisis.class);
	}

	private boolean agotado(String modelo) {
		LocalDate dia = agotados.get(modelo);
		return dia != null && dia.equals(LocalDate.now(reloj.withZone(CUPO)));
	}

	private boolean descansando(String modelo) {
		var hasta = descansando.get(modelo);
		return hasta != null && hasta.isAfter(reloj.instant());
	}

	private void descansar(String modelo, String motivo) {
		descansando.put(modelo, reloj.instant().plus(ajustes.descanso()));
		log.warn("Gemini: {} {}; descansa {} minutos", modelo, motivo, ajustes.descanso().toMinutes());
	}

	private void agotar(String modelo) {
		agotados.put(modelo, LocalDate.now(reloj.withZone(CUPO)));
		log.info("Gemini: {} no disponible hasta mañana", modelo);
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
