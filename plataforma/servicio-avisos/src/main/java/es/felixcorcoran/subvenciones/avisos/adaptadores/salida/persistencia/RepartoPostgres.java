package es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Avisos;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.EventosProcesados;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Novedades;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Pasadas;
import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeAvisos;
import es.felixcorcoran.subvenciones.avisos.dominio.Encaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Novedad;
import tools.jackson.databind.json.JsonMapper;

/** Novedades, avisos, pasadas y eventos aplicados en PostgreSQL */
public class RepartoPostgres implements Novedades, Avisos, Pasadas, EventosProcesados {

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final Clock reloj;

	public RepartoPostgres(JdbcClient jdbc, JsonMapper json, Clock reloj) {
		this.jdbc = jdbc;
		this.json = json;
		this.reloj = reloj;
	}

	@Override
	public void guardar(Novedad n) {
		jdbc.sql("""
				INSERT INTO novedades (codigo, titulo, categoria, cierre, llegada) VALUES (:codigo, :titulo, :categoria, :cierre, :llegada)
				ON CONFLICT (codigo) DO UPDATE SET titulo = EXCLUDED.titulo, categoria = EXCLUDED.categoria, cierre = EXCLUDED.cierre
				""")
				.param("codigo", n.codigo())
				.param("titulo", n.titulo())
				.param("categoria", n.categoria())
				.param("cierre", n.cierre() == null ? null : Date.valueOf(n.cierre()))
				.param("llegada", Timestamp.from(reloj.instant()))
				.update();
	}

	@Override
	public List<Novedad> sinRepartir() {
		return jdbc.sql("SELECT codigo, titulo, categoria, cierre FROM novedades WHERE NOT repartida ORDER BY llegada, codigo")
				.query((rs, i) -> new Novedad(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate()))
				.list();
	}

	@Override
	public void marcarRepartidas(List<String> codigos) {
		if (codigos.isEmpty()) return;
		jdbc.sql("UPDATE novedades SET repartida = true WHERE codigo IN (:codigos)").param("codigos", codigos).update();
	}

	@Override
	public void crear(UUID suscriptor, String codigo, Encaje.Estado estado, Instant ahora) {
		jdbc.sql("""
				INSERT INTO avisos (suscriptor_id, codigo, estado, creado) VALUES (:s, :codigo, :estado, :creado)
				ON CONFLICT (suscriptor_id, codigo) DO NOTHING
				""")
				.param("s", suscriptor)
				.param("codigo", codigo)
				.param("estado", estado.name())
				.param("creado", Timestamp.from(ahora))
				.update();
	}

	@Override
	public Map<UUID, List<String>> pendientes() {
		Map<UUID, List<String>> porPersona = new LinkedHashMap<>();
		jdbc.sql("SELECT suscriptor_id, codigo FROM avisos WHERE enviado IS NULL ORDER BY suscriptor_id, id")
				.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2)))
				.list()
				.forEach(e -> porPersona.computeIfAbsent(e.getKey(), k -> new java.util.ArrayList<>()).add(e.getValue()));
		return porPersona;
	}

	@Override
	public void marcarEnviados(UUID suscriptor, Instant ahora) {
		jdbc.sql("UPDATE avisos SET enviado = :ahora WHERE suscriptor_id = :s AND enviado IS NULL")
				.param("ahora", Timestamp.from(ahora))
				.param("s", suscriptor)
				.update();
	}

	@Override
	public long correosDesde(Instant desde) {
		return jdbc.sql("SELECT count(DISTINCT suscriptor_id) FROM avisos WHERE enviado >= :desde")
				.param("desde", Timestamp.from(desde))
				.query(Long.class)
				.single();
	}

	@Override
	public boolean hayAlguna() {
		return jdbc.sql("SELECT count(*) FROM pasadas").query(Long.class).single() > 0;
	}

	@Override
	public void apuntar(Instant cuando, ServicioDeAvisos.Resumen resumen) {
		jdbc.sql("INSERT INTO pasadas (fecha, resumen) VALUES (:fecha, CAST(:resumen AS jsonb))")
				.param("fecha", Timestamp.from(cuando))
				.param("resumen", json.writeValueAsString(resumen))
				.update();
	}

	@Override
	public boolean apuntar(UUID id) {
		return jdbc.sql("INSERT INTO eventos_procesados (id, procesado) VALUES (:id, now()) ON CONFLICT (id) DO NOTHING")
				.param("id", id)
				.update() == 1;
	}
}
