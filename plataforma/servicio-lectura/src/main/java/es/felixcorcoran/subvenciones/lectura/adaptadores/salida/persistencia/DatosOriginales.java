package es.felixcorcoran.subvenciones.lectura.adaptadores.salida.persistencia;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * La convocatoria tal como la publica la recogida. La lectura no necesita casi nada de ella, pero
 * la reenvía entera con lo leído: así el resto de servicios recibe el mismo formato que la web.
 */
public class DatosOriginales {

	private final JdbcClient jdbc;

	public DatosOriginales(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void guardar(String codigo, String datos) {
		jdbc.sql("""
				INSERT INTO lecturas (codigo, datos, actualizada) VALUES (:codigo, CAST(:datos AS jsonb), now())
				ON CONFLICT (codigo) DO UPDATE SET datos = EXCLUDED.datos, actualizada = now()
				""")
				.param("codigo", codigo)
				.param("datos", datos)
				.update();
	}
}
