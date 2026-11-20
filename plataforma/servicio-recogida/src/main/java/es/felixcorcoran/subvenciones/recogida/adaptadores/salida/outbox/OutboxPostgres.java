package es.felixcorcoran.subvenciones.recogida.adaptadores.salida.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.recogida.aplicacion.Eventos;
import es.felixcorcoran.subvenciones.recogida.dominio.Evento;
import tools.jackson.databind.json.JsonMapper;

/**
 * Anota cada evento en la tabla outbox, dentro de la transacción de quien llama. Sobre del evento:
 *
 * <pre>
 * {"id":"…uuid…","tipo":"convocatoria.cambiada","version":1,"ocurrido":"…",
 *  "convocatoria":{…formato de datos/convocatorias.json, sin la IA…},"campos":["fin"]}
 * </pre>
 *
 * «campos» solo va en convocatoria.cambiada.
 */
public class OutboxPostgres implements Eventos {

	public static final String TOPICO = "convocatorias";
	static final int VERSION = 1;

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final Clock reloj;
	private final Traza traza;

	public OutboxPostgres(JdbcClient jdbc, JsonMapper json, Clock reloj, Traza traza) {
		this.jdbc = jdbc;
		this.json = json;
		this.reloj = reloj;
		this.traza = traza;
	}

	@Override
	public void anotar(Evento evento) {
		UUID id = UUID.randomUUID();
		var ahora = reloj.instant();
		var sobre = new LinkedHashMap<String, Object>();
		sobre.put("id", id);
		sobre.put("tipo", evento.tipo());
		sobre.put("version", VERSION);
		sobre.put("ocurrido", ahora);
		sobre.put("convocatoria", evento.convocatoria());
		if (evento instanceof Evento.Cambiada cambiada) sobre.put("campos", cambiada.campos());

		jdbc.sql("""
				INSERT INTO outbox (id, topico, clave, carga, creado, traza)
				VALUES (:id, :topico, :clave, CAST(:carga AS jsonb), :creado, :traza)
				""")
				.param("id", id)
				.param("topico", TOPICO)
				.param("clave", evento.convocatoria().codigo())
				.param("carga", json.writeValueAsString(sobre))
				.param("creado", Timestamp.from(ahora))
				.param("traza", traza.actual())
				.update();
	}
}
