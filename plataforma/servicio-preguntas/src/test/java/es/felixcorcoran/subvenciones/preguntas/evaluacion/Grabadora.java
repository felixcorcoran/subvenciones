package es.felixcorcoran.subvenciones.preguntas.evaluacion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Graba y reproduce las respuestas de Gemini, para que la evaluación se pueda repetir en CI sin
 * clave, sin red y sin gastar cupo:
 *
 * <ul>
 * <li>REPRODUCIR (por defecto): responde con lo grabado; si una petición no está grabada, falla
 * diciendo cuál.</li>
 * <li>GRABAR: llama de verdad y guarda cada respuesta correcta.</li>
 * <li>REAL: llama de verdad y no guarda nada (el workflow manual de evaluación).</li>
 * </ul>
 *
 * Cada respuesta se guarda con el resumen (SHA-256) de la ruta y el cuerpo de la petición, sin la
 * clave: la misma pregunta sobre los mismos pasajes da siempre el mismo fichero.
 */
class Grabadora implements ClientHttpRequestInterceptor {

	enum Modo {
		REPRODUCIR, GRABAR, REAL
	}

	private final Modo modo;
	private final Path carpeta;
	int grabadas;
	int reproducidas;

	Grabadora(Modo modo, Path carpeta) {
		this.modo = modo;
		this.carpeta = carpeta;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo, ClientHttpRequestExecution ejecucion)
			throws IOException {
		Path fichero = carpeta.resolve(clave(peticion, cuerpo) + ".txt.gz");
		if (modo == Modo.REPRODUCIR) {
			if (!Files.exists(fichero)) {
				// Al grabar, los modelos que no respondieron (saturados) no siempre quedaron
				// grabados: se reproduce lo mismo que pasó, y la cadena pasa al siguiente modelo
				if (peticion.getURI().getPath().endsWith(":generateContent")) return respuesta(503, "{\"error\":\"no grabada\"}".getBytes());
				throw new IllegalStateException("Respuesta sin grabar para " + peticion.getURI().getPath()
						+ ": hay que volver a grabar la evaluación (-Devaluacion=grabar con GEMINI_API_KEY)");
			}
			reproducidas++;
			return grabada(leer(fichero));
		}
		ClientHttpResponse r = ejecucion.execute(peticion, cuerpo);
		byte[] datos = r.getBody().readAllBytes();
		int estado = r.getStatusCode().value();
		if (modo == Modo.GRABAR) {
			Files.createDirectories(carpeta);
			// Las correctas, tal cual; las demás (503, 429…), con su estado delante
			escribir(fichero, estado == 200 ? datos : concatenar((estado + "\n").getBytes(), datos));
			grabadas++;
		}
		return respuesta(estado, datos);
	}

	/** Una grabación: el cuerpo de una respuesta correcta, o «estado\ncuerpo» si no lo fue */
	private static ClientHttpResponse grabada(byte[] datos) {
		int salto = indice(datos, (byte) '\n');
		if (datos.length > 0 && datos[0] != '{' && salto > 0 && salto <= 3) {
			int estado = Integer.parseInt(new String(datos, 0, salto, StandardCharsets.US_ASCII));
			return respuesta(estado, java.util.Arrays.copyOfRange(datos, salto + 1, datos.length));
		}
		return respuesta(200, datos);
	}

	private static int indice(byte[] datos, byte b) {
		for (int i = 0; i < Math.min(datos.length, 8); i++) if (datos[i] == b) return i;
		return -1;
	}

	private static byte[] concatenar(byte[] a, byte[] b) {
		byte[] r = java.util.Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}

	private static String clave(HttpRequest peticion, byte[] cuerpo) {
		try {
			var sha = MessageDigest.getInstance("SHA-256");
			sha.update(peticion.getURI().getPath().getBytes(StandardCharsets.UTF_8));
			sha.update((byte) '\n');
			sha.update(cuerpo);
			return HexFormat.of().formatHex(sha.digest()).substring(0, 24);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] leer(Path fichero) {
		try (InputStream in = new GZIPInputStream(Files.newInputStream(fichero))) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void escribir(Path fichero, byte[] datos) throws IOException {
		try (var out = new GZIPOutputStream(Files.newOutputStream(fichero))) {
			out.write(datos);
		}
	}

	private static ClientHttpResponse respuesta(int estado, byte[] datos) {
		return new ClientHttpResponse() {
			@Override
			public HttpStatusCode getStatusCode() {
				return HttpStatusCode.valueOf(estado);
			}

			@Override
			public String getStatusText() {
				return "";
			}

			@Override
			public void close() {
			}

			@Override
			public InputStream getBody() {
				return new ByteArrayInputStream(datos);
			}

			@Override
			public HttpHeaders getHeaders() {
				var h = new HttpHeaders();
				h.setContentType(MediaType.APPLICATION_JSON);
				return h;
			}
		};
	}
}
