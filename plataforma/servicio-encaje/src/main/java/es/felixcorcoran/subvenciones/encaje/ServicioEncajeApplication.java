package es.felixcorcoran.subvenciones.encaje;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import es.felixcorcoran.subvenciones.encaje.adaptadores.salida.catalogo.CatalogoJson;
import es.felixcorcoran.subvenciones.encaje.adaptadores.salida.persistencia.ConvocatoriasPostgres;
import es.felixcorcoran.subvenciones.encaje.adaptadores.salida.persistencia.EventosProcesadosPostgres;
import es.felixcorcoran.subvenciones.encaje.aplicacion.Convocatorias;
import es.felixcorcoran.subvenciones.encaje.aplicacion.EventosProcesados;
import es.felixcorcoran.subvenciones.encaje.aplicacion.ServicioDeEncaje;
import es.felixcorcoran.subvenciones.encaje.dominio.Catalogo;
import es.felixcorcoran.subvenciones.encaje.dominio.ReglasDeEncaje;
import tools.jackson.databind.json.JsonMapper;

/**
 * El cableado vive aquí, para que el dominio y la aplicación no dependan de Spring
 * (docs/decisiones/0001-arquitectura-hexagonal.md).
 */
@SpringBootApplication
public class ServicioEncajeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicioEncajeApplication.class, args);
	}

	@Bean
	Clock reloj() {
		// Los plazos son días del calendario español
		return Clock.system(ZoneId.of("Europe/Madrid"));
	}

	@Bean
	Catalogo catalogo(JsonMapper json) {
		return new CatalogoJson(json);
	}

	@Bean
	Convocatorias convocatorias(JdbcClient jdbc, JsonMapper json) {
		return new ConvocatoriasPostgres(jdbc, json);
	}

	@Bean
	EventosProcesados eventosProcesados(JdbcClient jdbc) {
		return new EventosProcesadosPostgres(jdbc);
	}

	@Bean
	ServicioDeEncaje servicioDeEncaje(Convocatorias convocatorias, EventosProcesados eventos, Catalogo catalogo,
			Clock reloj) {
		return new ServicioDeEncaje(convocatorias, eventos, new ReglasDeEncaje(catalogo), catalogo, reloj);
	}
}
