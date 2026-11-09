package es.felixcorcoran.subvenciones.pasarela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * La pasarela: la única puerta de la plataforma hacia fuera. Reparte cada ruta a su servicio
 * (application.yaml), atiende CORS para la web del radar y frena el abuso de las rutas públicas
 * con un límite por IP. No valida tokens: lo hace cada servicio, para que una pasarela mal
 * configurada no abra nada (docs/decisiones/0007).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PasarelaApplication {

	public static void main(String[] args) {
		SpringApplication.run(PasarelaApplication.class, args);
	}
}
