package es.felixcorcoran.subvenciones.preguntas.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.gemini.GeminiHttp;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.IndiceEnMemoria;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas.SinBases;
import es.felixcorcoran.subvenciones.preguntas.dominio.Respuesta;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluación de «pregunta a la convocatoria» con preguntas reales sobre las bases de cinco
 * convocatorias (evaluacion/preguntas.json). Usa el servicio de verdad, Gemini de verdad (sus
 * respuestas grabadas) y un índice en memoria; pgvector se prueba aparte.
 *
 * Mide, pregunta a pregunta:
 * <ul>
 * <li><b>Acierta si consta:</b> dice que las bases lo dicen cuando lo dicen, y «no consta» cuando no.</li>
 * <li><b>Inventa:</b> responde algo cuando las bases no lo dicen. Es lo peor que puede pasar.</li>
 * <li><b>Página:</b> alguna cita está en una de las páginas donde está la respuesta.</li>
 * <li><b>Contenido:</b> la respuesta lleva el dato esperado (una de sus «claves»).</li>
 * <li><b>Citas descartadas:</b> citas que propuso la IA y no estaban tal cual en las bases.</li>
 * </ul>
 *
 * <pre>
 * ./mvnw test -pl servicio-preguntas -am -Dtest=EvaluacionTest                      # reproduce lo grabado (CI)
 * GEMINI_API_KEY=… ./mvnw test … -Dtest=EvaluacionTest -Devaluacion=grabar          # vuelve a grabar
 * GEMINI_API_KEY=… ./mvnw test … -Dtest=EvaluacionTest -Devaluacion=real            # contra Gemini, sin grabar
 * </pre>
 *
 * El informe queda en target/evaluacion.md.
 */
class EvaluacionTest {

	private static final Path RECURSOS = Path.of("src/test/resources/evaluacion");

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Conjunto(List<Convocatoria> convocatorias, List<Pregunta> preguntas) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Convocatoria(String codigo, String titulo, long documentoId, String nombre, Boolean sinTexto) {
		boolean escaneada() {
			return Boolean.TRUE.equals(sinTexto);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Pregunta(String id, String codigo, String pregunta, boolean consta, List<Integer> paginas, List<String> claves,
			boolean revisada) {
	}

	record Resultado(Pregunta p, Respuesta r) {
		boolean aciertaConsta() {
			return r.consta() == p.consta();
		}

		boolean inventa() {
			return !p.consta() && r.consta();
		}

		boolean paginaBuena() {
			return p.consta() && r.consta() && r.citas().stream().anyMatch(c -> p.paginas().contains(c.pagina()));
		}

		boolean contenidoBueno() {
			if (!p.consta() || !r.consta()) return false;
			String texto = r.texto().toLowerCase(Locale.ROOT);
			return p.claves().isEmpty() || p.claves().stream().anyMatch(c -> texto.contains(c.toLowerCase(Locale.ROOT)));
		}
	}

	@Test
	void preguntasRealesSobreBasesReales() throws Exception {
		Grabadora.Modo modo = Grabadora.Modo.valueOf(System.getProperty("evaluacion", "reproducir").toUpperCase(Locale.ROOT));
		String clave = modo == Grabadora.Modo.REPRODUCIR ? "sin-clave" : System.getenv("GEMINI_API_KEY");
		assertThat(clave).as("GEMINI_API_KEY para evaluar contra Gemini").isNotBlank();

		JsonMapper json = JsonMapper.builder().build();
		Conjunto conjunto = json.readValue(Files.readString(RECURSOS.resolve("preguntas.json")), Conjunto.class);
		Map<String, List<String>> paginas = new HashMap<>();
		for (Convocatoria c : conjunto.convocatorias()) {
			paginas.put(c.codigo(), json.readValue(Files.readString(RECURSOS.resolve("documentos/" + c.codigo() + ".json")),
					new TypeReference<List<String>>() {
					}));
		}

		var grabadora = new Grabadora(modo, RECURSOS.resolve("grabaciones"));
		var tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setReadTimeout(Duration.ofSeconds(90));
		RestClient http = RestClient.builder()
				.baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
				.requestFactory(tiempos)
				.requestInterceptor(grabadora)
				.build();
		Duration pausa = modo == Grabadora.Modo.REPRODUCIR ? Duration.ZERO : Duration.ofSeconds(6);
		var gemini = new GeminiHttp(http, json, new GeminiHttp.Ajustes(clave,
				List.of("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.7-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite"),
				"gemini-embedding-2", 768, pausa, Duration.ofMinutes(30)), Clock.systemUTC());

		var indice = new IndiceEnMemoria();
		var servicio = new ServicioDePreguntas(indice, indice,
				id -> String.valueOf(id).getBytes(),
				pdf -> paginas.get(codigoDe(conjunto, Long.parseLong(new String(pdf)))),
				gemini, gemini, id -> true, Runnable::run);
		for (Convocatoria c : conjunto.convocatorias()) servicio.alRecibir(UUID.randomUUID(), c.codigo(), c.titulo(), c.documentoId(), c.nombre());
		servicio.indexarPendientes(conjunto.convocatorias().size());

		// Unas bases escaneadas no se pueden consultar, y se dice: no se inventa una respuesta
		for (Convocatoria c : conjunto.convocatorias().stream().filter(Convocatoria::escaneada).toList()) {
			try {
				servicio.preguntar(c.codigo(), "¿Cuál es el plazo de presentación?");
				throw new AssertionError(c.codigo() + " no tiene texto y ha respondido");
			} catch (SinBases e) {
				assertThat(e.getMessage()).contains("no tiene texto");
			}
		}

		List<Resultado> resultados = new ArrayList<>();
		for (Pregunta p : conjunto.preguntas()) resultados.add(new Resultado(p, servicio.preguntar(p.codigo(), p.pregunta())));

		String informe = informe(resultados, modo, grabadora);
		Files.createDirectories(Path.of("target"));
		Files.writeString(Path.of("target/evaluacion.md"), informe);
		System.out.println(informe);

		long constan = resultados.stream().filter(r -> r.p().consta()).count();
		assertThat(resultados.stream().filter(Resultado::inventa)).as("respuestas inventadas").isEmpty();
		assertThat(resultados.stream().filter(Resultado::aciertaConsta).count()).as("acierta si consta").isGreaterThanOrEqualTo(Math.round(resultados.size() * 0.85));
		assertThat(resultados.stream().filter(Resultado::paginaBuena).count()).as("cita en la página buena").isGreaterThanOrEqualTo(Math.round(constan * 0.8));
	}

	private static String codigoDe(Conjunto conjunto, long documentoId) {
		return conjunto.convocatorias().stream().filter(c -> c.documentoId() == documentoId).findFirst().orElseThrow().codigo();
	}

	private static String informe(List<Resultado> rs, Grabadora.Modo modo, Grabadora grabadora) {
		long constan = rs.stream().filter(r -> r.p().consta()).count();
		long noConstan = rs.size() - constan;
		StringBuilder s = new StringBuilder("# Evaluación de «pregunta a la convocatoria»\n\n");
		s.append("Modo: ").append(modo.name().toLowerCase(Locale.ROOT)).append(" · ")
				.append(rs.size()).append(" preguntas (").append(constan).append(" con respuesta en las bases, ")
				.append(noConstan).append(" sin ella) · revisadas por una persona: ")
				.append(rs.stream().filter(r -> r.p().revisada()).count()).append("\n\n");
		s.append("| Métrica | Resultado |\n| --- | --- |\n");
		s.append(fila("Acierta si consta o no", rs.stream().filter(Resultado::aciertaConsta).count(), rs.size()));
		s.append(fila("Inventa una respuesta (debe ser 0)", rs.stream().filter(Resultado::inventa).count(), noConstan));
		s.append(fila("Cita en la página buena", rs.stream().filter(Resultado::paginaBuena).count(), constan));
		s.append(fila("Respuesta con el dato esperado", rs.stream().filter(Resultado::contenidoBueno).count(), constan));
		s.append("| Citas descartadas por no ser literales | ").append(rs.stream().mapToInt(r -> r.r().citasDescartadas()).sum())
				.append(" de ").append(rs.stream().mapToInt(r -> r.r().citas().size() + r.r().citasDescartadas()).sum()).append(" |\n\n");
		s.append("| Pregunta | Esperado | Obtenido | Páginas citadas | Bien |\n| --- | --- | --- | --- | --- |\n");
		for (Resultado r : rs) {
			boolean bien = r.aciertaConsta() && (!r.p().consta() || (r.paginaBuena() && r.contenidoBueno()));
			s.append("| ").append(r.p().id()).append(" | ").append(r.p().consta() ? "consta, p. " + r.p().paginas() : "no consta")
					.append(" | ").append(r.r().consta() ? r.r().texto().replace("|", "/").replace("\n", " ") : "no consta")
					.append(" | ").append(r.r().citas().stream().map(c -> String.valueOf(c.pagina())).distinct().toList())
					.append(" | ").append(bien ? "sí" : "**no**").append(" |\n");
		}
		if (modo != Grabadora.Modo.REAL) {
			s.append("\nRespuestas de Gemini: ").append(grabadora.reproducidas).append(" reproducidas, ")
					.append(grabadora.grabadas).append(" grabadas.\n");
		}
		return s.toString();
	}

	private static String fila(String metrica, long bien, long total) {
		return "| " + metrica + " | " + bien + " de " + total + (total == 0 ? "" : " (" + Math.round(100.0 * bien / total) + " %)") + " |\n";
	}
}
