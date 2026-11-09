package es.felixcorcoran.subvenciones.pasarela;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite de peticiones por IP en las rutas públicas, por ventanas de tiempo fijas: cuántas veces
 * puede cada IP llamar a cada ruta en cada ventana. Al pasarse, 429 con Retry-After. Solo se
 * guarda la IP en memoria mientras dura su ventana.
 *
 * Con una sola réplica de la pasarela basta la memoria; con varias, el contador tendría que ir a
 * un almacén compartido (Redis).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimitePorIp extends OncePerRequestFilter {

	/** «POST /api/v1/suscripciones», 5 peticiones cada 10 minutos */
	public record Limite(String ruta, int veces, Duration cada) {
	}

	@ConfigurationProperties("subvenciones.pasarela")
	public record Ajustes(List<Limite> limites) {
		public Ajustes {
			limites = limites == null ? List.of() : List.copyOf(limites);
		}
	}

	private record Regla(HttpMethod metodo, PathPattern ruta, Limite limite) {
	}

	private record Ventana(Instant fin, int veces) {
	}

	private final List<Regla> reglas;
	private final Clock reloj;
	private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

	@org.springframework.beans.factory.annotation.Autowired
	public LimitePorIp(Ajustes ajustes) {
		this(ajustes, Clock.systemUTC());
	}

	LimitePorIp(Ajustes ajustes, Clock reloj) {
		this.reloj = reloj;
		var parser = PathPatternParser.defaultInstance;
		this.reglas = ajustes.limites().stream().map(l -> {
			String[] partes = l.ruta().strip().split("\\s+", 2);
			return partes.length == 2
					? new Regla(HttpMethod.valueOf(partes[0]), parser.parse(partes[1]), l)
					: new Regla(null, parser.parse(partes[0]), l);
		}).toList();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		var ruta = PathContainer.parsePath(peticion.getRequestURI());
		for (int i = 0; i < reglas.size(); i++) {
			Regla r = reglas.get(i);
			if (r.metodo() != null && !r.metodo().matches(peticion.getMethod())) continue;
			if (!r.ruta().matches(ruta)) continue;
			Instant ahora = reloj.instant();
			String clave = i + "|" + peticion.getRemoteAddr();
			Ventana v = ventanas.compute(clave, (k, antes) -> antes == null || !antes.fin().isAfter(ahora)
					? new Ventana(ahora.plus(r.limite().cada()), 1)
					: new Ventana(antes.fin(), antes.veces() + 1));
			if (v.veces() > r.limite().veces()) {
				long segundos = Math.max(1, Duration.between(ahora, v.fin()).toSeconds());
				respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
				respuesta.setHeader("Retry-After", String.valueOf(segundos));
				respuesta.setContentType("application/problem+json");
				respuesta.getWriter().write("{\"status\":429,\"detail\":\"Demasiadas peticiones; prueba dentro de " + segundos + " segundos\"}");
				return;
			}
			break;
		}
		ventanas.values().removeIf(v -> !v.fin().isAfter(reloj.instant()));
		cadena.doFilter(peticion, respuesta);
	}
}
