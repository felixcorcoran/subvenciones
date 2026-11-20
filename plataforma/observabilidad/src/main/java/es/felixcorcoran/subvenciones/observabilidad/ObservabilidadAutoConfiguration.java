package es.felixcorcoran.subvenciones.observabilidad;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;

import io.micrometer.observation.ObservationPredicate;

/**
 * Lo que no se traza, porque solo es ruido: en una prueba real, las comprobaciones de salud, el
 * relevo de la outbox (que mira cada segundo si hay algo) y los filtros de Spring Security
 * tapaban las trazas que interesan.
 *
 * El relevo sigue trazando cada evento que publica: esa traza la abre él mismo, dentro de la de la
 * petición que anotó el evento.
 */
@AutoConfiguration
public class ObservabilidadAutoConfiguration {

	@Bean
	ObservationPredicate sinRuido() {
		return (nombre, contexto) -> {
			if (nombre.startsWith("spring.security.")) return false;
			if (contexto instanceof ServerRequestObservationContext peticion) {
				return !peticion.getCarrier().getRequestURI().startsWith("/actuator");
			}
			if (contexto instanceof ScheduledTaskObservationContext tarea) {
				return !tarea.getTargetClass().getSimpleName().equals("RelevoOutbox");
			}
			return true;
		};
	}
}
