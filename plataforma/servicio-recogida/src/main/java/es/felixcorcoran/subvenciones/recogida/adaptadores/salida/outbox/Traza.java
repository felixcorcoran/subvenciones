package es.felixcorcoran.subvenciones.recogida.adaptadores.salida.outbox;

import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * La traza a través de la outbox. El evento se anota en una petición y se publica después, en otro
 * hilo: sin esto, la traza se cortaría ahí. Al anotar se guarda el contexto de la traza en curso
 * (formato W3C «traceparent») y, al publicar, se continúa: el envío a Kafka queda dentro de la
 * misma traza que la petición que lo originó.
 *
 * Sin trazas configuradas (pruebas sin observabilidad) no hace nada.
 */
public class Traza {

	private final Tracer tracer;
	private final Propagator propagador;

	public Traza(Tracer tracer, Propagator propagador) {
		this.tracer = tracer;
		this.propagador = propagador;
	}

	/** El «traceparent» de la traza en curso, o null si no hay */
	public String actual() {
		if (tracer == null) return null;
		Span span = tracer.currentSpan();
		if (span == null) return null;
		TraceContext c = span.context();
		return "00-" + c.traceId() + "-" + c.spanId() + "-01";
	}

	/** Hace el trabajo dentro de la traza guardada (o sin traza, si no la hay) */
	public void continuar(String traceparent, String nombre, Runnable trabajo) {
		if (tracer == null || propagador == null || traceparent == null) {
			trabajo.run();
			return;
		}
		Span span = propagador.extract(Map.of("traceparent", traceparent), Map::get).name(nombre).start();
		try (Tracer.SpanInScope ambito = tracer.withSpan(span)) {
			trabajo.run();
		} catch (RuntimeException e) {
			span.error(e);
			throw e;
		} finally {
			span.end();
		}
	}
}
