package es.felixcorcoran.subvenciones.lectura.adaptadores.salida.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Eventos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Anota «convocatoria.leida» en la outbox, dentro de la transacción de quien llama:
 *
 * <pre>
 * {"id":"…","tipo":"convocatoria.leida","version":1,"ocurrido":"…",
 *  "convocatoria":{…lo que publicó la recogida…,"ia":{…},"iaModelo":"…","cierreDescartado":{…}}}
 * </pre>
 *
 * Es el mismo formato que datos/convocatorias.json de la web, el que ya leen el encaje y alertas.
 */
public class OutboxPostgres implements Eventos {

	public static final String TOPICO = "convocatorias.leidas";

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final Clock reloj;

	public OutboxPostgres(JdbcClient jdbc, JsonMapper json, Clock reloj) {
		this.jdbc = jdbc;
		this.json = json;
		this.reloj = reloj;
	}

	@Override
	public void anotarLeida(String codigo) {
		record Fila(String datos, String lectura) {
		}
		Fila fila = jdbc.sql("SELECT datos::text, lectura::text FROM lecturas WHERE codigo = :codigo")
				.param("codigo", codigo)
				.query((rs, i) -> new Fila(rs.getString(1), rs.getString(2)))
				.single();
		if (fila.datos() == null) throw new IllegalStateException("Sin los datos de la recogida para " + codigo);

		ObjectNode convocatoria = (ObjectNode) json.readTree(fila.datos());
		JsonNode lectura = json.readTree(fila.lectura());
		convocatoria.set("ia", lectura.get("ia"));
		convocatoria.set("iaModelo", lectura.get("modelo"));
		JsonNode descartado = lectura.get("cierreDescartado");
		if (descartado != null && !descartado.isNull()) convocatoria.set("cierreDescartado", descartado);
		else convocatoria.remove("cierreDescartado");

		UUID id = UUID.randomUUID();
		var ahora = reloj.instant();
		ObjectNode sobre = json.createObjectNode();
		sobre.put("id", id.toString());
		sobre.put("tipo", "convocatoria.leida");
		sobre.put("version", 1);
		sobre.put("ocurrido", ahora.toString());
		sobre.set("convocatoria", convocatoria);

		jdbc.sql("""
				INSERT INTO outbox (id, topico, clave, carga, creado)
				VALUES (:id, :topico, :clave, CAST(:carga AS jsonb), :creado)
				""")
				.param("id", id)
				.param("topico", TOPICO)
				.param("clave", codigo)
				.param("carga", json.writeValueAsString(sobre))
				.param("creado", Timestamp.from(ahora))
				.update();
	}
}
