package es.felixcorcoran.subvenciones.recogida.adaptadores.salida.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica en Kafka lo anotado en la outbox, en orden, y lo marca como publicado.
 *
 * <ul>
 * <li>Envía de uno en uno y espera la confirmación (acks=all) antes del siguiente: los eventos de
 * una convocatoria salen en el orden en que se anotaron.</li>
 * <li>Si un envío falla, la transacción se deshace y el lote se reintenta en la siguiente vuelta. Lo
 * ya enviado de ese lote saldrá otra vez: es «al menos una vez», y los consumidores descartan los
 * repetidos por el id del evento.</li>
 * <li>FOR UPDATE SKIP LOCKED: si hay dos réplicas del servicio, no publican lo mismo a la vez.</li>
 * </ul>
 */
public class RelevoOutbox {

	record Pendiente(long orden, String topico, String clave, String carga) {
	}

	private static final int LOTE = 100;

	private final JdbcClient jdbc;
	private final KafkaTemplate<String, String> kafka;
	private final TransactionTemplate transaccion;

	public RelevoOutbox(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transaccion) {
		this.jdbc = jdbc;
		this.kafka = kafka;
		this.transaccion = transaccion;
	}

	@Scheduled(fixedDelayString = "${subvenciones.outbox.cada:1s}")
	public void publicarPendientes() {
		Integer publicados;
		do {
			publicados = transaccion.execute(estado -> publicarLote());
		} while (publicados != null && publicados == LOTE);
	}

	private int publicarLote() {
		List<Pendiente> pendientes = jdbc.sql("""
				SELECT orden, topico, clave, carga::text FROM outbox
				WHERE publicado IS NULL ORDER BY orden LIMIT :lote FOR UPDATE SKIP LOCKED
				""")
				.param("lote", LOTE)
				.query((rs, i) -> new Pendiente(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)))
				.list();
		for (Pendiente p : pendientes) {
			try {
				kafka.send(p.topico(), p.clave(), p.carga()).get(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrumpido publicando la outbox", e);
			} catch (Exception e) {
				throw new IllegalStateException("No se pudo publicar el evento " + p.orden() + " en " + p.topico(), e);
			}
		}
		if (!pendientes.isEmpty()) {
			jdbc.sql("UPDATE outbox SET publicado = now() WHERE orden IN (:ordenes)")
					.param("ordenes", pendientes.stream().map(Pendiente::orden).toList())
					.update();
		}
		return pendientes.size();
	}

	/** Lo publicado hace más de una semana ya no hace falta */
	@Scheduled(cron = "0 0 4 * * *", zone = "Europe/Madrid")
	public void limpiar() {
		jdbc.sql("DELETE FROM outbox WHERE publicado < now() - interval '7 days'").update();
	}
}
