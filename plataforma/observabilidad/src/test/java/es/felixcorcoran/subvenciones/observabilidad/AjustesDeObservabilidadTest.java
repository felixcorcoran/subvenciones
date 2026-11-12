package es.felixcorcoran.subvenciones.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AjustesDeObservabilidadTest {

	private static StandardEnvironment entorno(Map<String, Object> propiedades) {
		var e = new StandardEnvironment();
		e.getPropertySources().addFirst(new MapPropertySource("prueba", propiedades));
		new AjustesDeObservabilidad().postProcessEnvironment(e, new SpringApplication());
		return e;
	}

	@Test
	void sinColectorNoSeExportaNadaPeroSiSeTrazaKafka() {
		var e = entorno(Map.of());
		assertThat(e.getProperty("management.tracing.export.otlp.enabled")).isEqualTo("false");
		assertThat(e.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
		assertThat(e.getProperty("spring.kafka.listener.observation-enabled")).isEqualTo("true");
		assertThat(e.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
		assertThat(e.getProperty("management.metrics.distribution.percentiles-histogram.http.server.requests")).isEqualTo("true");
	}

	@Test
	void conColectorSeExportanTrazasYMetricasAlMismoSitio() {
		var e = entorno(Map.of(AjustesDeObservabilidad.VARIABLE, "http://lgtm:4318/"));
		assertThat(e.getProperty("management.tracing.export.otlp.enabled")).isEqualTo("true");
		assertThat(e.getProperty("management.opentelemetry.tracing.export.otlp.endpoint")).isEqualTo("http://lgtm:4318/v1/traces");
		assertThat(e.getProperty("management.otlp.metrics.export.url")).isEqualTo("http://lgtm:4318/v1/metrics");
	}

	@Test
	void laConfiguracionDeCadaServicioMandaSobreEstosValores() {
		var e = entorno(Map.of("management.tracing.sampling.probability", "0.1"));
		assertThat(e.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
	}
}
