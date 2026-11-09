package es.felixcorcoran.subvenciones.preguntas;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.bdns.DescargasBdns;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.gemini.GeminiHttp;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.pdf.LectorPdfBox;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.persistencia.EventosProcesadosPostgres;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.persistencia.IndicePostgres;
import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.persistencia.TransaccionesSpring;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Descargas;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas;
import tools.jackson.databind.json.JsonMapper;

/** El cableado vive aquí, para que el dominio y la aplicación no dependan de Spring */
@SpringBootApplication
@EnableScheduling
public class ServicioPreguntasApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicioPreguntasApplication.class, args);
	}

	@Bean
	Clock reloj() {
		return Clock.system(ZoneId.of("Europe/Madrid"));
	}

	@Bean
	IndicePostgres indice(JdbcClient jdbc) {
		return new IndicePostgres(jdbc);
	}

	@Bean
	Descargas descargas(RestClient.Builder builder, @Value("${subvenciones.bdns.url}") String url) {
		var tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofSeconds(60));
		return new DescargasBdns(builder.clone().baseUrl(url).requestFactory(tiempos)
				.defaultHeader("User-Agent", "subvenciones.felixcorcoran.es (radar de ayudas para pymes)").build(),
				Duration.ofMillis(400));
	}

	@Bean
	GeminiHttp gemini(RestClient.Builder builder, JsonMapper json, Clock reloj,
			@Value("${subvenciones.gemini.url}") String url,
			@Value("${subvenciones.gemini.clave:}") String clave,
			@Value("${subvenciones.gemini.modelos}") String modelos,
			@Value("${subvenciones.gemini.modelo-vectores}") String modeloVectores,
			@Value("${subvenciones.gemini.pausa:6s}") Duration pausa,
			@Value("${subvenciones.gemini.descanso:30m}") Duration descanso) {
		var tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofSeconds(60));
		var lista = Arrays.stream(modelos.split(",")).map(String::strip).filter(m -> !m.isEmpty()).toList();
		return new GeminiHttp(builder.clone().baseUrl(url).requestFactory(tiempos).build(), json,
				new GeminiHttp.Ajustes(clave, lista, modeloVectores, 768, pausa, descanso), reloj);
	}

	@Bean
	ServicioDePreguntas servicioDePreguntas(IndicePostgres indice, Descargas descargas, GeminiHttp gemini, JdbcClient jdbc,
			TransactionTemplate transaccion) {
		return new ServicioDePreguntas(indice, indice, descargas, new LectorPdfBox(), gemini, gemini,
				new EventosProcesadosPostgres(jdbc), new TransaccionesSpring(transaccion));
	}
}
