package es.felixcorcoran.subvenciones.lectura;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.bdns.DocumentosBdns;
import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.gemini.GeminiHttp;
import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.outbox.OutboxPostgres;
import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.outbox.RelevoOutbox;
import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.persistencia.DatosOriginales;
import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.persistencia.EventosProcesadosPostgres;
import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.persistencia.LecturasPostgres;
import es.felixcorcoran.subvenciones.lectura.adaptadores.salida.persistencia.TransaccionesSpring;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Documentos;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.LectorIa;
import es.felixcorcoran.subvenciones.lectura.aplicacion.Puertos.Lecturas;
import es.felixcorcoran.subvenciones.lectura.aplicacion.ServicioDeLectura;
import tools.jackson.databind.json.JsonMapper;

/** El cableado vive aquí, para que el dominio y la aplicación no dependan de Spring */
@SpringBootApplication
@EnableScheduling
public class ServicioLecturaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicioLecturaApplication.class, args);
	}

	@Bean
	Clock reloj() {
		return Clock.system(ZoneId.of("Europe/Madrid"));
	}

	@Bean
	Lecturas lecturas(JdbcClient jdbc, JsonMapper json) {
		return new LecturasPostgres(jdbc, json);
	}

	@Bean
	DatosOriginales datosOriginales(JdbcClient jdbc) {
		return new DatosOriginales(jdbc);
	}

	@Bean
	Documentos documentos(RestClient.Builder builder, @Value("${subvenciones.bdns.url}") String url) {
		var tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofSeconds(60));
		RestClient http = builder.clone().baseUrl(url).requestFactory(tiempos)
				.defaultHeader("User-Agent", "subvenciones.felixcorcoran.es (radar de ayudas para pymes)")
				.build();
		return new DocumentosBdns(http, Duration.ofMillis(400));
	}

	@Bean
	LectorIa lectorIa(RestClient.Builder builder, JsonMapper json, Clock reloj,
			@Value("${subvenciones.gemini.url}") String url,
			@Value("${subvenciones.gemini.clave:}") String clave,
			@Value("${subvenciones.gemini.modelos}") String modelos,
			@Value("${subvenciones.gemini.pausa:6s}") Duration pausa,
			@Value("${subvenciones.gemini.espera-por-minuto:30s}") Duration esperaPorMinuto,
			@Value("${subvenciones.gemini.descanso:30m}") Duration descanso) {
		var tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofMinutes(3));
		RestClient http = builder.clone().baseUrl(url).requestFactory(tiempos).build();
		var lista = Arrays.stream(modelos.split(",")).map(String::strip).filter(m -> !m.isEmpty()).toList();
		return new GeminiHttp(http, json, new GeminiHttp.Ajustes(clave, lista, pausa, esperaPorMinuto, descanso), reloj);
	}

	@Bean
	OutboxPostgres outbox(JdbcClient jdbc, JsonMapper json, Clock reloj) {
		return new OutboxPostgres(jdbc, json, reloj);
	}

	@Bean
	RelevoOutbox relevoOutbox(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transaccion) {
		return new RelevoOutbox(jdbc, kafka, transaccion);
	}

	@Bean
	ServicioDeLectura servicioDeLectura(Lecturas lecturas, Documentos documentos, LectorIa ia, OutboxPostgres outbox,
			JdbcClient jdbc, TransactionTemplate transaccion, Clock reloj,
			@Value("${subvenciones.lectura.max-por-pasada:10}") int maxPorPasada) {
		return new ServicioDeLectura(lecturas, documentos, ia, outbox, new EventosProcesadosPostgres(jdbc),
				new TransaccionesSpring(transaccion), reloj, maxPorPasada);
	}
}
