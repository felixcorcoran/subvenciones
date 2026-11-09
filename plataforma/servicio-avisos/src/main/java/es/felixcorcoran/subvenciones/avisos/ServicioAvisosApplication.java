package es.felixcorcoran.subvenciones.avisos;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.correo.CorreoRegistro;
import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.correo.CorreoSmtp;
import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.encaje.EncajesHttp;
import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia.ProvinciasJson;
import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia.RepartoPostgres;
import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia.SuscriptoresPostgres;
import es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia.TransaccionesSpring;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Plantillas;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Encajes;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Suscriptores;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Transacciones;
import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeAvisos;
import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeSuscripciones;
import tools.jackson.databind.json.JsonMapper;

/** El cableado vive aquí, para que el dominio y la aplicación no dependan de Spring */
@SpringBootApplication
@EnableScheduling
public class ServicioAvisosApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicioAvisosApplication.class, args);
	}

	@Bean
	Clock reloj() {
		return Clock.system(ZoneId.of("Europe/Madrid"));
	}

	@Bean
	Suscriptores suscriptores(JdbcClient jdbc) {
		return new SuscriptoresPostgres(jdbc);
	}

	@Bean
	RepartoPostgres reparto(JdbcClient jdbc, JsonMapper json, Clock reloj) {
		return new RepartoPostgres(jdbc, json, reloj);
	}

	@Bean
	Transacciones transacciones(TransactionTemplate plantilla) {
		return new TransaccionesSpring(plantilla);
	}

	@Bean
	Encajes encajes(RestClient.Builder builder, @Value("${subvenciones.encaje.url}") String url) {
		var tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(5));
		tiempos.setReadTimeout(Duration.ofSeconds(30));
		return new EncajesHttp(builder.clone().baseUrl(url).requestFactory(tiempos).build());
	}

	/** Con SMTP configurado (spring.mail.host), por SMTP; si no, solo al log */
	@Bean
	Correo correo(ObjectProvider<JavaMailSender> smtp, @Value("${spring.mail.host:}") String host,
			@Value("${subvenciones.avisos.remitente}") String remitente) {
		return host.isBlank() ? new CorreoRegistro() : new CorreoSmtp(smtp.getObject(), remitente);
	}

	@Bean
	ProvinciasJson provincias(JsonMapper json) {
		return new ProvinciasJson(json);
	}

	@Bean
	Plantillas plantillas(ProvinciasJson provincias, @Value("${subvenciones.avisos.web}") String web, @Value("${subvenciones.avisos.api}") String api,
			@Value("${subvenciones.avisos.radar}") String radar) {
		return new Plantillas(new Plantillas.Direcciones(web, api, radar), provincias);
	}

	@Bean
	ServicioDeSuscripciones servicioDeSuscripciones(Suscriptores suscriptores, ProvinciasJson provincias, Plantillas plantillas,
			Correo correo, Transacciones transacciones, Clock reloj) {
		return new ServicioDeSuscripciones(suscriptores, provincias, plantillas, correo, transacciones, reloj);
	}

	@Bean
	ServicioDeAvisos servicioDeAvisos(Suscriptores suscriptores, RepartoPostgres reparto, Encajes encajes, Correo correo,
			Plantillas plantillas, ServicioDeSuscripciones suscripciones, Transacciones transacciones, Clock reloj,
			@Value("${subvenciones.avisos.max-correos-dia:90}") int maxCorreosDia) {
		return new ServicioDeAvisos(suscriptores, reparto, reparto, reparto, encajes, correo, plantillas, suscripciones,
				reparto, transacciones, reloj, maxCorreosDia);
	}
}
