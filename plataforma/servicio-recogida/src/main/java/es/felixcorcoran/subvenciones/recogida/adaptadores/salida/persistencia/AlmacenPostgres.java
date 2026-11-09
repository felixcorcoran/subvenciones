package es.felixcorcoran.subvenciones.recogida.adaptadores.salida.persistencia;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.recogida.aplicacion.Almacen;
import es.felixcorcoran.subvenciones.recogida.aplicacion.Resumen;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria;
import tools.jackson.databind.json.JsonMapper;

/** Las convocatorias, las descartadas y las pasadas en PostgreSQL */
public class AlmacenPostgres implements Almacen {

	private final JdbcClient jdbc;
	private final JsonMapper json;

	public AlmacenPostgres(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public Optional<Convocatoria> guardada(String codigo) {
		return jdbc.sql("SELECT documento FROM convocatorias WHERE codigo = :codigo")
				.param("codigo", codigo)
				.query((rs, i) -> json.readValue(rs.getString(1), Convocatoria.class))
				.optional();
	}

	@Override
	public List<Convocatoria> guardadas() {
		return jdbc.sql("SELECT documento FROM convocatorias ORDER BY codigo")
				.query((rs, i) -> json.readValue(rs.getString(1), Convocatoria.class))
				.list();
	}

	@Override
	public void guardar(Convocatoria c) {
		jdbc.sql("""
				INSERT INTO convocatorias (codigo, documento, actualizada)
				VALUES (:codigo, CAST(:documento AS jsonb), now())
				ON CONFLICT (codigo) DO UPDATE SET documento = EXCLUDED.documento, actualizada = now()
				""")
				.param("codigo", c.codigo())
				.param("documento", json.writeValueAsString(c))
				.update();
	}

	@Override
	public void retirar(String codigo) {
		jdbc.sql("DELETE FROM convocatorias WHERE codigo = :codigo").param("codigo", codigo).update();
	}

	@Override
	public boolean descartada(String codigo) {
		return jdbc.sql("SELECT count(*) FROM descartadas WHERE codigo = :codigo")
				.param("codigo", codigo)
				.query(Integer.class)
				.single() > 0;
	}

	@Override
	public void descartar(String codigo, String motivo, LocalDate fecha) {
		jdbc.sql("""
				INSERT INTO descartadas (codigo, motivo, fecha) VALUES (:codigo, :motivo, :fecha)
				ON CONFLICT (codigo) DO UPDATE SET motivo = EXCLUDED.motivo, fecha = EXCLUDED.fecha
				""")
				.param("codigo", codigo)
				.param("motivo", motivo)
				.param("fecha", Date.valueOf(fecha))
				.update();
	}

	@Override
	public void olvidarDescartadasAntesDe(LocalDate fecha) {
		jdbc.sql("DELETE FROM descartadas WHERE fecha < :fecha").param("fecha", Date.valueOf(fecha)).update();
	}

	@Override
	public Optional<Instant> ultimaPasada() {
		return jdbc.sql("SELECT fecha FROM pasadas ORDER BY fecha DESC LIMIT 1")
				.query((rs, i) -> rs.getTimestamp(1))
				.optional()
				.map(Timestamp::toInstant);
	}

	@Override
	public void apuntarPasada(Instant cuando, Resumen resumen) {
		jdbc.sql("INSERT INTO pasadas (fecha, resumen) VALUES (:fecha, CAST(:resumen AS jsonb))")
				.param("fecha", Timestamp.from(cuando))
				.param("resumen", json.writeValueAsString(resumen))
				.update();
	}
}
