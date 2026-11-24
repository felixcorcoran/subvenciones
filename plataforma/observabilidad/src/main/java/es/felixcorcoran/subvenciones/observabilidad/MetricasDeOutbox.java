package es.felixcorcoran.subvenciones.observabilidad;

import java.time.Clock;
import java.time.Duration;

import org.springframework.jdbc.core.simple.JdbcClient;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Lo que queda por publicar en la outbox: {@code subvenciones.outbox.pendientes} (eventos) y
 * {@code subvenciones.outbox.antiguedad} (segundos que lleva esperando el más antiguo, 0 si no hay
 * ninguno). Si Kafka no responde, la outbox guarda los eventos sin perderlos, pero sin esto nadie se
 * enteraría de que no salen.
 *
 * Los servicios con outbox lo declaran como bean y Spring Boot lo registra. La tabla {@code outbox}
 * solo se consulta cuando se exportan las métricas (una vez por minuto).
 */
public final class MetricasDeOutbox implements MeterBinder {

	public static final String PENDIENTES = "subvenciones.outbox.pendientes";
	public static final String ANTIGUEDAD = "subvenciones.outbox.antiguedad";

	private final JdbcClient jdbc;
	private final Clock reloj;

	public MetricasDeOutbox(JdbcClient jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	@Override
	public void bindTo(MeterRegistry registro) {
		Gauge.builder(PENDIENTES, this, MetricasDeOutbox::pendientes)
				.description("Eventos de la outbox sin publicar en Kafka")
				.register(registro);
		Gauge.builder(ANTIGUEDAD, this, MetricasDeOutbox::antiguedad)
				.description("Segundos que lleva sin publicar el evento más antiguo de la outbox")
				.baseUnit("seconds")
				.register(registro);
	}

	double pendientes() {
		return jdbc.sql("SELECT count(*) FROM outbox WHERE publicado IS NULL").query(Long.class).single();
	}

	double antiguedad() {
		var masAntiguo = jdbc.sql("SELECT min(creado) FROM outbox WHERE publicado IS NULL")
				.query(java.sql.Timestamp.class).optional();
		return masAntiguo
				.map(t -> Math.max(0, Duration.between(t.toInstant(), reloj.instant()).toMillis() / 1000.0))
				.orElse(0.0);
	}
}
