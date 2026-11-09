package es.felixcorcoran.subvenciones.preguntas.adaptadores.entrada.programada;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas;

/**
 * Indexa poco a poco las bases que llegan (cada hora, unas pocas), para que la primera pregunta no
 * tenga que esperar. Una sin indexar se indexa al preguntarle. Con «-» no se programa.
 */
@Component
class IndexadoProgramado {

	private final ServicioDePreguntas servicio;
	private final int porPasada;

	IndexadoProgramado(ServicioDePreguntas servicio, @Value("${subvenciones.preguntas.indexar-por-pasada:10}") int porPasada) {
		this.servicio = servicio;
		this.porPasada = porPasada;
	}

	@Scheduled(cron = "${subvenciones.preguntas.cron:0 30 * * * *}", zone = "Europe/Madrid")
	void cadaHora() {
		servicio.indexarPendientes(porPasada);
	}
}
