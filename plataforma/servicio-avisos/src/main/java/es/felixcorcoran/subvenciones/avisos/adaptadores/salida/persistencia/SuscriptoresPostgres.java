package es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Suscriptores;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;
import es.felixcorcoran.subvenciones.avisos.dominio.Suscriptor;

/** Los suscriptores en PostgreSQL, en columnas: solo email, perfil, token y fechas */
public class SuscriptoresPostgres implements Suscriptores {

	private static final String COLUMNAS = "id, email, provincia, municipio, forma, empleados, antiguedad, sector, token, estado, consentimiento, creado, confirmado, ultimo_alta, ultimo_aviso";

	private final JdbcClient jdbc;

	public SuscriptoresPostgres(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Suscriptor> porEmail(String email) {
		return uno("email = :v", email);
	}

	@Override
	public Optional<Suscriptor> porToken(String token) {
		return uno("token = :v", token);
	}

	@Override
	public Optional<Suscriptor> porId(UUID id) {
		return uno("id = :v", id);
	}

	@Override
	public List<Suscriptor> activos() {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM suscriptores WHERE estado = 'ACTIVO' ORDER BY creado")
				.query(SuscriptoresPostgres::fila)
				.list();
	}

	@Override
	public void guardar(Suscriptor s) {
		Perfil p = s.perfil();
		jdbc.sql("""
				INSERT INTO suscriptores (%s)
				VALUES (:id, :email, :provincia, :municipio, :forma, :empleados, :antiguedad, :sector, :token, :estado,
				        :consentimiento, :creado, :confirmado, :ultimoAlta, :ultimoAviso)
				ON CONFLICT (id) DO UPDATE SET provincia = EXCLUDED.provincia, municipio = EXCLUDED.municipio,
				  forma = EXCLUDED.forma, empleados = EXCLUDED.empleados, antiguedad = EXCLUDED.antiguedad,
				  sector = EXCLUDED.sector, estado = EXCLUDED.estado, consentimiento = EXCLUDED.consentimiento,
				  confirmado = EXCLUDED.confirmado, ultimo_alta = EXCLUDED.ultimo_alta, ultimo_aviso = EXCLUDED.ultimo_aviso
				""".formatted(COLUMNAS))
				.param("id", s.id())
				.param("email", s.email())
				.param("provincia", p.provincia())
				.param("municipio", p.municipio())
				.param("forma", p.forma())
				.param("empleados", p.empleados())
				.param("antiguedad", p.antiguedad())
				.param("sector", p.sector())
				.param("token", s.token())
				.param("estado", s.estado().name())
				.param("consentimiento", s.consentimiento())
				.param("creado", ts(s.creado()))
				.param("confirmado", ts(s.confirmado()))
				.param("ultimoAlta", ts(s.ultimoAlta()))
				.param("ultimoAviso", ts(s.ultimoAviso()))
				.update();
	}

	@Override
	public void borrar(UUID id) {
		jdbc.sql("DELETE FROM suscriptores WHERE id = :id").param("id", id).update();
	}

	@Override
	public long borrarPendientesCreadosAntesDe(Instant limite) {
		return jdbc.sql("DELETE FROM suscriptores WHERE estado = 'PENDIENTE' AND creado < :limite")
				.param("limite", ts(limite))
				.update();
	}

	private Optional<Suscriptor> uno(String condicion, Object valor) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM suscriptores WHERE " + condicion)
				.param("v", valor)
				.query(SuscriptoresPostgres::fila)
				.optional();
	}

	private static Suscriptor fila(ResultSet rs, int i) throws SQLException {
		var perfil = new Perfil(rs.getString("provincia"), rs.getString("municipio"), rs.getString("forma"),
				rs.getInt("empleados"), rs.getString("antiguedad"), rs.getString("sector"));
		return new Suscriptor(rs.getObject("id", UUID.class), rs.getString("email"), perfil, rs.getString("token"),
				Suscriptor.Estado.valueOf(rs.getString("estado")), rs.getString("consentimiento"),
				instante(rs.getTimestamp("creado")), instante(rs.getTimestamp("confirmado")),
				instante(rs.getTimestamp("ultimo_alta")), instante(rs.getTimestamp("ultimo_aviso")));
	}

	static Timestamp ts(Instant i) {
		return i == null ? null : Timestamp.from(i);
	}

	static Instant instante(Timestamp t) {
		return t == null ? null : t.toInstant();
	}
}
