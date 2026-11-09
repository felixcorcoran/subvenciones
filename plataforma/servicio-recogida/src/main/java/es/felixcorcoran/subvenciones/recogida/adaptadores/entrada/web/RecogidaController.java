package es.felixcorcoran.subvenciones.recogida.adaptadores.entrada.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.felixcorcoran.subvenciones.recogida.aplicacion.Resumen;
import es.felixcorcoran.subvenciones.recogida.aplicacion.ServicioDeRecogida;
import es.felixcorcoran.subvenciones.recogida.aplicacion.ServicioDeRecogida.RecogidaEnMarcha;

/** Lanzar una pasada a mano. En la fase 4 quedará solo para administradores (OAuth2) */
@RestController
@RequestMapping("/api/v1/recogidas")
class RecogidaController {

	private final ServicioDeRecogida servicio;

	RecogidaController(ServicioDeRecogida servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	Resumen recoger() {
		return servicio.recoger();
	}

	@ExceptionHandler(RecogidaEnMarcha.class)
	ProblemDetail enMarcha(RecogidaEnMarcha e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}
}
