package es.felixcorcoran.subvenciones.avisos.adaptadores.entrada.programada;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeAvisos;

/** La pasada de cada mañana, a las 9:00 de Madrid (tras la recogida y las primeras lecturas). Con «-» no se programa */
@Component
class PasadaProgramada {

	private static final Logger log = LoggerFactory.getLogger(PasadaProgramada.class);

	private final ServicioDeAvisos servicio;

	PasadaProgramada(ServicioDeAvisos servicio) {
		this.servicio = servicio;
	}

	@Scheduled(cron = "${subvenciones.avisos.cron:0 0 9 * * *}", zone = "Europe/Madrid")
	void cadaManana() {
		try {
			servicio.pasada();
		} catch (IllegalStateException e) {
			log.info("Ya había una pasada de avisos en marcha; esta se salta");
		} catch (RuntimeException e) {
			// Por ejemplo, el servicio de encaje no responde: se repite mañana, sin perder nada
			log.error("La pasada de avisos ha fallado: {}", e.getMessage());
		}
	}
}
