package es.felixcorcoran.subvenciones.pasarela;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS en la pasarela, antes que nada (también antes del límite por IP, para que una respuesta 429
 * llegue al navegador con sus cabeceras CORS). Solo las webs del radar y del panel, y sin cookies:
 * la API se usa con token o sin nada.
 */
@Configuration
class Cors {

	@Bean
	FilterRegistrationBean<CorsFilter> filtroCors(@Value("${subvenciones.pasarela.origenes}") List<String> origenes) {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(origenes);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setAllowCredentials(false);
		config.setMaxAge(Duration.ofHours(1));
		var fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", config);
		var registro = new FilterRegistrationBean<>(new CorsFilter(fuente));
		registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registro;
	}
}
