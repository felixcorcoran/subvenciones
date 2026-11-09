package es.felixcorcoran.subvenciones.encaje.adaptadores.salida.persistencia;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import es.felixcorcoran.subvenciones.encaje.aplicacion.Convocatorias;
import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las convocatorias en PostgreSQL: la convocatoria entera en JSONB y la fecha de cierre en su
 * columna, para que «abiertas tal día» sea una consulta y no un recorrido en memoria.
 */
public class ConvocatoriasPostgres implements Convocatorias {

	private final JdbcClient jdbc;
	private final JsonMapper json;

	public ConvocatoriasPostgres(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	@Transactional
	public void guardar(Convocatoria c) {
		jdbc.sql("""
				INSERT INTO convocatorias (codigo, cierre, documento, actualizada)
				VALUES (:codigo, :cierre, CAST(:documento AS jsonb), now())
				ON CONFLICT (codigo) DO UPDATE
				SET cierre = EXCLUDED.cierre, documento = EXCLUDED.documento, actualizada = now()
				""")
				.param("codigo", c.codigo())
				.param("cierre", c.cierre() == null ? null : Date.valueOf(c.cierre()))
				.param("documento", json.writeValueAsString(c))
				.update();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Convocatoria> abiertasEl(LocalDate dia) {
		return jdbc.sql("""
				SELECT documento FROM convocatorias
				WHERE cierre IS NULL OR cierre >= :dia
				ORDER BY codigo
				""")
				.param("dia", Date.valueOf(dia))
				.query((rs, i) -> json.readValue(rs.getString("documento"), Convocatoria.class))
				.list();
	}
}
