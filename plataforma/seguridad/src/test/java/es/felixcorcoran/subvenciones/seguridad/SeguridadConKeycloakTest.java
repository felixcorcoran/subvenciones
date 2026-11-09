package es.felixcorcoran.subvenciones.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las reglas de acceso contra un Keycloak de verdad, con el mismo realm que docker compose
 * (infra/keycloak/realm-subvenciones.json): tokens pedidos con client credentials, firmados por
 * Keycloak y validados por el servicio.
 */
@SpringBootTest(classes = AplicacionDePrueba.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "subvenciones.seguridad.publicas=GET /publica", "subvenciones.seguridad.admin=POST /admin/**" })
@Testcontainers(disabledWithoutDocker = true)
class SeguridadConKeycloakTest {

	private static final Path REALM = Path.of("../infra/keycloak/realm-subvenciones.json");

	static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.2")
			.withCommand("start-dev", "--import-realm")
			.withCopyFileToContainer(MountableFile.forHostPath(REALM), "/opt/keycloak/data/import/realm-subvenciones.json")
			.withExposedPorts(8080)
			.waitingFor(Wait.forHttp("/realms/subvenciones").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

	static {
		if (DockerClientFactory.instance().isDockerAvailable()) keycloak.start();
	}

	@DynamicPropertySource
	static void emisor(DynamicPropertyRegistry r) {
		r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", SeguridadConKeycloakTest::realm);
	}

	static String realm() {
		return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + "/realms/subvenciones";
	}

	@LocalServerPort
	int puerto;

	private RestClient servicio() {
		return RestClient.builder().baseUrl("http://localhost:" + puerto)
				.defaultStatusHandler(s -> true, (peticion, respuesta) -> {
				}).build();
	}

	/** Un token de Keycloak para un cliente del realm (client credentials), con el secreto del realm local */
	private static String token(String cliente) throws Exception {
		JsonNode r = JsonMapper.builder().build().readTree(Files.readString(REALM));
		String secreto = null;
		for (JsonNode c : r.path("clients")) if (cliente.equals(c.path("clientId").asString())) secreto = c.path("secret").asString();
		var formulario = new LinkedMultiValueMap<String, String>();
		formulario.add("grant_type", "client_credentials");
		formulario.add("client_id", cliente);
		formulario.add("client_secret", secreto);
		Map<?, ?> respuesta = RestClient.create().post()
				.uri(realm() + "/protocol/openid-connect/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(formulario)
				.retrieve()
				.body(Map.class);
		return (String) respuesta.get("access_token");
	}

	private HttpStatus estado(String metodo, String ruta, String token) {
		var peticion = "GET".equals(metodo) ? servicio().get().uri(ruta) : servicio().post().uri(ruta);
		if (token != null) peticion = peticion.header("Authorization", "Bearer " + token);
		return HttpStatus.valueOf(peticion.retrieve().toBodilessEntity().getStatusCode().value());
	}

	@Test
	void lasRutasPublicasNoPidenToken() {
		assertThat(estado("GET", "/publica", null)).isEqualTo(HttpStatus.OK);
		assertThat(estado("GET", "/actuator/health", null)).isEqualTo(HttpStatus.OK);
	}

	@Test
	void sinTokenOConUnTokenFalsoNoSePasa() {
		assertThat(estado("GET", "/privada", null)).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(estado("POST", "/admin/pasada", null)).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(estado("POST", "/admin/pasada", "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ5byJ9.firma-falsa")).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void unTokenValidoSinElRolDeAdministracionEntraEnLoPrivadoPeroNoEnLoDeAdministracion() throws Exception {
		String t = token("solo-lectura");
		assertThat(estado("GET", "/privada", t)).isEqualTo(HttpStatus.OK);
		assertThat(estado("POST", "/admin/pasada", t)).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void conElRolDeAdministracionDeKeycloakSePuedeLanzarUnaPasada() throws Exception {
		assertThat(estado("POST", "/admin/pasada", token("operaciones"))).isEqualTo(HttpStatus.OK);
	}

	@Test
	void unaRutaQueNoEstaEnNingunaListaPideUsuario() {
		assertThat(estado("GET", "/cualquier-otra", null)).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
