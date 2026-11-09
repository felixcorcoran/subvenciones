package es.felixcorcoran.subvenciones.lectura.adaptadores.entrada.programada;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import es.felixcorcoran.subvenciones.lectura.aplicacion.ServicioDeLectura;

/**
 * Una pasada cada hora de 7:00 a 22:00 (hora de Madrid): lo recogido por la mañana se va leyendo
 * a lo largo del día, al ritmo que deja el nivel gratuito de la IA. Con «-» no se programa.
 */
@Component
class LecturaProgramada {

	private static final Logger log = LoggerFactory.getLogger(LecturaProgramada.class);

	private final ServicioDeLectura servicio;

	LecturaProgramada(ServicioDeLectura servicio) {
		this.servicio = servicio;
	}

	@Scheduled(cron = "${subvenciones.lectura.cron:0 0 7-22 * * *}", zone = "Europe/Madrid")
	void cadaHora() {
		try {
			servicio.leerPendientes();
		} catch (IllegalStateException e) {
			log.info("Ya había una lectura en marcha; esta se salta");
		}
	}
}
