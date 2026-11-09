package es.felixcorcoran.subvenciones.lectura.adaptadores.entrada.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.felixcorcoran.subvenciones.lectura.aplicacion.ServicioDeLectura;
import es.felixcorcoran.subvenciones.lectura.aplicacion.ServicioDeLectura.Resumen;

/** Lanzar una pasada de lectura a mano. En la fase 4 quedará solo para administradores */
@RestController
@RequestMapping("/api/v1/lecturas")
class LecturaController {

	private final ServicioDeLectura servicio;

	LecturaController(ServicioDeLectura servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	Resumen leer() {
		return servicio.leerPendientes();
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail enMarcha(IllegalStateException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}
}
