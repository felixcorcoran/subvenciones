package es.felixcorcoran.subvenciones.observabilidad;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * La observabilidad de todos los servicios, configurada en un solo sitio:
 *
 * <ul>
 * <li><b>Trazas</b> de todas las peticiones (muestreo del 100 %: hay poco tráfico y se quiere ver
 * todo) y de Kafka: el productor pone la cabecera {@code traceparent} y el consumidor la continúa,
 * así una convocatoria se sigue de servicio en servicio.</li>
 * <li><b>Métricas</b> de Micrometer por OTLP.</li>
 * <li>El identificador de traza ya va en cada línea del log (Spring Boot lo añade al patrón).</li>
 * </ul>
 *
 * Solo se exporta si hay a dónde: con {@code OTEL_EXPORTER_OTLP_ENDPOINT} (la variable estándar de
 * OpenTelemetry, por ejemplo {@code http://lgtm:4318}). Sin ella, nada sale del proceso: ni las
 * pruebas ni un arranque en local llenan el log de errores de conexión.
 *
 * Son valores por defecto: la configuración de cada servicio puede cambiar cualquiera.
 */
public class AjustesDeObservabilidad implements EnvironmentPostProcessor, Ordered {

	static final String VARIABLE = "OTEL_EXPORTER_OTLP_ENDPOINT";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
		Map<String, Object> ajustes = new LinkedHashMap<>();
		ajustes.put("management.tracing.sampling.probability", "1.0");
		ajustes.put("spring.kafka.template.observation-enabled", "true");
		ajustes.put("spring.kafka.listener.observation-enabled", "true");
		// Los cubos del histograma de las peticiones HTTP, para la latencia p95 del panel
		ajustes.put("management.metrics.distribution.percentiles-histogram.http.server.requests", "true");
		// Los logs no se exportan por OTLP: llevan el id de traza y se leen con el resto del contenedor
		ajustes.put("management.logging.export.otlp.enabled", "false");

		String destino = entorno.getProperty(VARIABLE);
		boolean exportar = destino != null && !destino.isBlank();
		ajustes.put("management.tracing.export.otlp.enabled", String.valueOf(exportar));
		ajustes.put("management.otlp.metrics.export.enabled", String.valueOf(exportar));
		if (exportar) {
			String base = destino.strip().replaceAll("/+$", "");
			ajustes.put("management.opentelemetry.tracing.export.otlp.endpoint", base + "/v1/traces");
			ajustes.put("management.otlp.metrics.export.url", base + "/v1/metrics");
			ajustes.put("management.otlp.metrics.export.step", "15s");
		}
		// Al final de la lista: cualquier otra fuente (application.yaml, variables) manda sobre estos
		entorno.getPropertySources().addLast(new MapPropertySource("observabilidad", ajustes));
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
