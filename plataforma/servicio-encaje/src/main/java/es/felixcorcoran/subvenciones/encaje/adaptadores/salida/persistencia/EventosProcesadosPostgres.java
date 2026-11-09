package es.felixcorcoran.subvenciones.encaje.adaptadores.salida.persistencia;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.encaje.aplicacion.EventosProcesados;

/**
 * Los eventos aplicados, en la misma base de datos que las convocatorias: el apunte y el cambio se
 * confirman en la misma transacción. Si dos copias del mismo evento llegan a la vez, ON CONFLICT
 * hace esperar a la segunda hasta que la primera termina, y entonces no inserta nada.
 */
public class EventosProcesadosPostgres implements EventosProcesados {

	private final JdbcClient jdbc;

	public EventosProcesadosPostgres(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean apuntar(UUID idEvento) {
		return jdbc.sql("INSERT INTO eventos_procesados (id, procesado) VALUES (:id, now()) ON CONFLICT (id) DO NOTHING")
				.param("id", idEvento)
				.update() == 1;
	}
}
