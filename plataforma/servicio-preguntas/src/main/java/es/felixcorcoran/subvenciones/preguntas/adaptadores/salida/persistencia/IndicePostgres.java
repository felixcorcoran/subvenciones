package es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Documentos;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Pasajes;
import es.felixcorcoran.subvenciones.preguntas.dominio.Documento;
import es.felixcorcoran.subvenciones.preguntas.dominio.Pasaje;

/**
 * Los documentos y sus pasajes en PostgreSQL con pgvector. La búsqueda es por distancia del coseno
 * ({@code <=>}) y solo dentro de la convocatoria que se pregunta.
 */
public class IndicePostgres implements Documentos, Pasajes {

	private final JdbcClient jdbc;

	public IndicePostgres(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Documento> buscar(String codigo) {
		return jdbc.sql("SELECT codigo, titulo, documento_id, nombre, estado, intentos, motivo FROM documentos WHERE codigo = :c")
				.param("c", codigo)
				.query(IndicePostgres::documento)
				.optional();
	}

	@Override
	public void guardar(Documento d) {
		jdbc.sql("""
				INSERT INTO documentos (codigo, titulo, documento_id, nombre, estado, intentos, motivo, actualizado)
				VALUES (:codigo, :titulo, :documentoId, :nombre, :estado, :intentos, :motivo, now())
				ON CONFLICT (codigo) DO UPDATE SET titulo = EXCLUDED.titulo, documento_id = EXCLUDED.documento_id,
				  nombre = EXCLUDED.nombre, estado = EXCLUDED.estado, intentos = EXCLUDED.intentos,
				  motivo = EXCLUDED.motivo, actualizado = now()
				""")
				.param("codigo", d.codigo())
				.param("titulo", d.titulo())
				.param("documentoId", d.documentoId())
				.param("nombre", d.nombre())
				.param("estado", d.estado().name())
				.param("intentos", d.intentos())
				.param("motivo", d.motivo())
				.update();
	}

	@Override
	public List<Documento> pendientes(int maximo) {
		return jdbc.sql("""
				SELECT codigo, titulo, documento_id, nombre, estado, intentos, motivo FROM documentos
				WHERE estado = 'PENDIENTE' ORDER BY intentos, actualizado LIMIT :maximo
				""")
				.param("maximo", maximo)
				.query(IndicePostgres::documento)
				.list();
	}

	@Override
	public void reemplazar(String codigo, List<Pasaje> pasajes, List<float[]> vectores) {
		jdbc.sql("DELETE FROM pasajes WHERE codigo = :c").param("c", codigo).update();
		for (int i = 0; i < pasajes.size(); i++) {
			Pasaje p = pasajes.get(i);
			jdbc.sql("INSERT INTO pasajes (codigo, orden, pagina, texto, vector) VALUES (:codigo, :orden, :pagina, :texto, CAST(:vector AS vector))")
					.param("codigo", codigo)
					.param("orden", p.orden())
					.param("pagina", p.pagina())
					.param("texto", p.texto())
					.param("vector", vector(vectores.get(i)))
					.update();
		}
	}

	@Override
	public List<Pasaje> parecidos(String codigo, float[] pregunta, int cuantos) {
		return jdbc.sql("""
				SELECT codigo, orden, pagina, texto FROM pasajes WHERE codigo = :c
				ORDER BY vector <=> CAST(:q AS vector) LIMIT :n
				""")
				.param("c", codigo)
				.param("q", vector(pregunta))
				.param("n", cuantos)
				.query((rs, i) -> new Pasaje(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4)))
				.list();
	}

	/** El formato de texto de pgvector: [0.1,0.2,…] */
	static String vector(float[] v) {
		StringBuilder s = new StringBuilder("[");
		for (int i = 0; i < v.length; i++) {
			if (i > 0) s.append(',');
			s.append(v[i]);
		}
		return s.append(']').toString();
	}

	private static Documento documento(ResultSet rs, int i) throws SQLException {
		long id = rs.getLong("documento_id");
		return new Documento(rs.getString("codigo"), rs.getString("titulo"), rs.wasNull() ? null : id,
				rs.getString("nombre"), Documento.Estado.valueOf(rs.getString("estado")), rs.getInt("intentos"),
				rs.getString("motivo"));
	}
}
