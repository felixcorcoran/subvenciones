package es.felixcorcoran.subvenciones.pasarela;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/**
 * La pasarela con cinco servicios de mentira (un servidor HTTP cada uno, que responde con su
 * nombre): a dónde va cada ruta, que el token pasa, el límite por IP y CORS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "LIMITE_ENCAJES=3")
class PasarelaTest {

	/** Lo que ha recibido cada servicio: «MÉTODO /ruta | Authorization» */
	static final Map<String, List<String>> RECIBIDO = new LinkedHashMap<>();
	static final Map<String, HttpServer> SERVICIOS = new LinkedHashMap<>();

	static {
		for (String nombre : List.of("encaje", "recogida", "lectura", "avisos", "preguntas")) {
			try {
				HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
				List<String> recibido = new CopyOnWriteArrayList<>();
				s.createContext("/", intercambio -> {
					recibido.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath() + " | "
							+ intercambio.getRequestHeaders().getFirst("Authorization"));
					byte[] cuerpo = ("{\"servicio\":\"" + nombre + "\"}").getBytes(StandardCharsets.UTF_8);
					intercambio.getResponseHeaders().add("Content-Type", "application/json");
					intercambio.sendResponseHeaders(200, cuerpo.length);
					intercambio.getResponseBody().write(cuerpo);
					intercambio.close();
				});
				s.start();
				SERVICIOS.put(nombre, s);
				RECIBIDO.put(nombre, recibido);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@DynamicPropertySource
	static void servicios(DynamicPropertyRegistry r) {
		SERVICIOS.forEach((nombre, s) -> r.add(nombre.toUpperCase() + "_URL", () -> "http://127.0.0.1:" + s.getAddress().getPort()));
	}

	@AfterAll
	static void parar() {
		SERVICIOS.values().forEach(s -> s.stop(0));
	}

	@LocalServerPort
	int puerto;

	private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, Map<String, String> cabeceras) {
		var peticion = RestClient.builder().baseUrl("http://localhost:" + puerto)
				.defaultStatusHandler(s -> true, (p, r) -> {
				}).build()
				.method(metodo).uri(ruta).contentType(MediaType.APPLICATION_JSON);
		cabeceras.forEach(peticion::header);
		if (metodo == HttpMethod.POST || metodo == HttpMethod.PUT) peticion = peticion.body("{}");
		return peticion.retrieve().toEntity(String.class);
	}

	@Test
	void cadaRutaVaASuServicio() {
		Map<String, String> rutas = new LinkedHashMap<>();
		rutas.put("/api/v1/convocatorias/940577/preguntas", "preguntas");
		rutas.put("/api/v1/indices", "preguntas");
		rutas.put("/api/v1/convocatorias/940577", "encaje");
		rutas.put("/api/v1/recogidas", "recogida");
		rutas.put("/api/v1/lecturas", "lectura");
		rutas.put("/api/v1/avisos/pasada", "avisos");
		rutas.put("/api/v1/baja/" + "a".repeat(43), "avisos");
		rutas.forEach((ruta, servicio) -> assertThat(pedir(HttpMethod.POST, ruta, Map.of()).getBody())
				.as(ruta).contains("\"" + servicio + "\""));
	}

	@Test
	void elTokenLlegaTalCualAlServicio() {
		pedir(HttpMethod.POST, "/api/v1/recogidas", Map.of("Authorization", "Bearer token-de-prueba"));
		assertThat(RECIBIDO.get("recogida")).contains("POST /api/v1/recogidas | Bearer token-de-prueba");
	}

	@Test
	void unaRutaQueNoEsDeNingunServicioNoExiste() {
		assertThat(pedir(HttpMethod.GET, "/api/v1/otra-cosa", Map.of()).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void pasadoElLimitePorIpResponde429ConRetryAfter() {
		for (int i = 0; i < 3; i++) {
			assertThat(pedir(HttpMethod.POST, "/api/v1/encajes", Map.of()).getStatusCode().value()).isEqualTo(200);
		}
		var cuarta = pedir(HttpMethod.POST, "/api/v1/encajes", Map.of());
		assertThat(cuarta.getStatusCode().value()).isEqualTo(429);
		assertThat(cuarta.getHeaders().getFirst("Retry-After")).isNotBlank();
		assertThat(RECIBIDO.get("encaje").stream().filter(r -> r.startsWith("POST /api/v1/encajes"))).hasSize(3);
	}

	@Test
	void corsSoloParaLasWebsDelRadar() {
		Map<String, String> desdeElRadar = Map.of("Origin", "https://subvenciones.felixcorcoran.es",
				"Access-Control-Request-Method", "POST");
		var bien = pedir(HttpMethod.OPTIONS, "/api/v1/convocatorias/1/preguntas", desdeElRadar);
		assertThat(bien.getStatusCode().value()).isEqualTo(200);
		assertThat(bien.getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://subvenciones.felixcorcoran.es");

		var mal = pedir(HttpMethod.OPTIONS, "/api/v1/convocatorias/1/preguntas",
				Map.of("Origin", "https://otra-web.example", "Access-Control-Request-Method", "POST"));
		assertThat(mal.getStatusCode().value()).isEqualTo(403);
	}
}
