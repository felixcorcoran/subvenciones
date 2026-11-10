package es.felixcorcoran.subvenciones.recogida;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import org.apache.kafka.clients.admin.NewTopic;

import es.felixcorcoran.subvenciones.recogida.adaptadores.salida.bdns.BdnsHttp;
import es.felixcorcoran.subvenciones.recogida.adaptadores.salida.outbox.OutboxPostgres;
import es.felixcorcoran.subvenciones.recogida.adaptadores.salida.outbox.RelevoOutbox;
import es.felixcorcoran.subvenciones.recogida.adaptadores.salida.outbox.Traza;
import es.felixcorcoran.subvenciones.recogida.adaptadores.salida.persistencia.AlmacenPostgres;
import es.felixcorcoran.subvenciones.recogida.adaptadores.salida.persistencia.TransaccionesSpring;
import es.felixcorcoran.subvenciones.recogida.aplicacion.Almacen;
import es.felixcorcoran.subvenciones.recogida.aplicacion.Bdns;
import es.felixcorcoran.subvenciones.recogida.aplicacion.Eventos;
import es.felixcorcoran.subvenciones.recogida.aplicacion.ServicioDeRecogida;
import tools.jackson.databind.json.JsonMapper;

/** El cableado vive aquí, para que el dominio y la aplicación no dependan de Spring */
@SpringBootApplication
@EnableScheduling
public class ServicioRecogidaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicioRecogidaApplication.class, args);
	}

	@Bean
	Clock reloj() {
		// Los plazos son días del calendario español
		return Clock.system(ZoneId.of("Europe/Madrid"));
	}

	@Bean
	NewTopic topicoConvocatorias() {
		return TopicBuilder.name(OutboxPostgres.TOPICO).partitions(3).build();
	}

	@Bean
	Bdns bdns(RestClient.Builder builder, @Value("${subvenciones.bdns.url}") String url,
			@Value("${subvenciones.bdns.pausa:400ms}") Duration pausa,
			@Value("${subvenciones.bdns.espera-reintento:5s}") Duration esperaReintento) {
		var tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofSeconds(30));
		RestClient http = builder.baseUrl(url)
				.requestFactory(tiempos)
				.defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader("User-Agent", "subvenciones.felixcorcoran.es (radar de ayudas para pymes)")
				.build();
		return new BdnsHttp(http, new BdnsHttp.Ritmo(pausa, esperaReintento));
	}

	@Bean
	Almacen almacen(JdbcClient jdbc, JsonMapper json) {
		return new AlmacenPostgres(jdbc, json);
	}

	@Bean
	Eventos eventos(JdbcClient jdbc, JsonMapper json, Clock reloj, Traza traza) {
		return new OutboxPostgres(jdbc, json, reloj, traza);
	}

	@Bean
	RelevoOutbox relevoOutbox(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transaccion, Traza traza) {
		return new RelevoOutbox(jdbc, kafka, transaccion, traza);
	}

	/** La traza a través de la outbox; sin trazas configuradas, no hace nada */
	@Bean
	Traza traza(org.springframework.beans.factory.ObjectProvider<io.micrometer.tracing.Tracer> tracer,
			org.springframework.beans.factory.ObjectProvider<io.micrometer.tracing.propagation.Propagator> propagador) {
		return new Traza(tracer.getIfAvailable(), propagador.getIfAvailable());
	}

	@Bean
	ServicioDeRecogida servicioDeRecogida(Bdns bdns, Almacen almacen, Eventos eventos, TransactionTemplate transaccion,
			Clock reloj, @Value("${subvenciones.recogida.dias-atras:14}") int diasAtras,
			@Value("${subvenciones.recogida.max-comprobar:200}") int maxComprobar) {
		return new ServicioDeRecogida(bdns, almacen, eventos, new TransaccionesSpring(transaccion), reloj,
				new ServicioDeRecogida.Ajustes(diasAtras, maxComprobar));
	}
}
