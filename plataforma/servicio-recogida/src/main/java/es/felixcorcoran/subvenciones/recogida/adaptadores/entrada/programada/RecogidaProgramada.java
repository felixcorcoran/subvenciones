package es.felixcorcoran.subvenciones.recogida.adaptadores.entrada.programada;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import es.felixcorcoran.subvenciones.recogida.aplicacion.ServicioDeRecogida;
import es.felixcorcoran.subvenciones.recogida.aplicacion.ServicioDeRecogida.RecogidaEnMarcha;

/** La pasada de cada mañana. Con subvenciones.recogida.cron=- no se programa (pruebas) */
@Component
class RecogidaProgramada {

	private static final Logger log = LoggerFactory.getLogger(RecogidaProgramada.class);

	private final ServicioDeRecogida servicio;

	RecogidaProgramada(ServicioDeRecogida servicio) {
		this.servicio = servicio;
	}

	@Scheduled(cron = "${subvenciones.recogida.cron:0 30 6 * * *}", zone = "Europe/Madrid")
	void cadaManana() {
		try {
			servicio.recoger();
		} catch (RecogidaEnMarcha e) {
			log.info("Ya había una recogida en marcha; esta se salta");
		}
	}
}
