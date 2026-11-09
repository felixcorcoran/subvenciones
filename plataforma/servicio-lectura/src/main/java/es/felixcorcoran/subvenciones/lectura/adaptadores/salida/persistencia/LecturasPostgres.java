package es.felixcorcoran.subvenciones.lectura.adaptadores.salida.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Lecturas;
import es.felixcorcoran.subvenciones.lectura.dominio.Lectura;
import tools.jackson.databind.json.JsonMapper;

/** Las lecturas en PostgreSQL (columna «lectura»); los datos originales van aparte (DatosOriginales) */
public class LecturasPostgres implements Lecturas {

	private final JdbcClient jdbc;
	private final JsonMapper json;

	public LecturasPostgres(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public Optional<Lectura> buscar(String codigo) {
		return jdbc.sql("SELECT lectura::text FROM lecturas WHERE codigo = :codigo AND lectura IS NOT NULL")
				.param("codigo", codigo)
				.query((rs, i) -> json.readValue(rs.getString(1), Lectura.class))
				.optional();
	}

	@Override
	public List<Lectura> todas() {
		return jdbc.sql("SELECT lectura::text FROM lecturas WHERE lectura IS NOT NULL ORDER BY codigo")
				.query((rs, i) -> json.readValue(rs.getString(1), Lectura.class))
				.list();
	}

	@Override
	public void guardar(Lectura l) {
		jdbc.sql("""
				INSERT INTO lecturas (codigo, lectura, actualizada) VALUES (:codigo, CAST(:lectura AS jsonb), now())
				ON CONFLICT (codigo) DO UPDATE SET lectura = EXCLUDED.lectura, actualizada = now()
				""")
				.param("codigo", l.codigo())
				.param("lectura", json.writeValueAsString(l))
				.update();
	}
}
