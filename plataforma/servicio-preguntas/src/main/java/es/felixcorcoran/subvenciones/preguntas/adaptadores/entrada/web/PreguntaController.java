package es.felixcorcoran.subvenciones.preguntas.adaptadores.entrada.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.IaNoDisponible;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas.NoEncontrada;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas.PreguntaNoValida;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas.SinBases;
import es.felixcorcoran.subvenciones.preguntas.dominio.Respuesta;

/**
 * «Pregunta a la convocatoria»:
 *
 * <pre>
 * POST /api/v1/convocatorias/940577/preguntas  {"pregunta":"¿Pueden pedirla los autónomos?"}
 * → {"texto":"…","consta":true,"citas":[{"pagina":3,"texto":"…"}],…}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
class PreguntaController {

	record Pregunta(String pregunta) {
	}

	private final ServicioDePreguntas servicio;

	PreguntaController(ServicioDePreguntas servicio) {
		this.servicio = servicio;
	}

	@PostMapping("/convocatorias/{codigo}/preguntas")
	Respuesta preguntar(@PathVariable String codigo, @RequestBody Pregunta p) {
		return servicio.preguntar(codigo, p.pregunta());
	}

	/** Indexa lo pendiente a mano. En la fase 4 quedará solo para administradores */
	@PostMapping("/indices")
	ServicioDePreguntas.Resumen indexar() {
		return servicio.indexarPendientes(50);
	}

	@ExceptionHandler(NoEncontrada.class)
	ProblemDetail noEncontrada(NoEncontrada e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(SinBases.class)
	ProblemDetail sinBases(SinBases e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
	}

	@ExceptionHandler(PreguntaNoValida.class)
	ProblemDetail noValida(PreguntaNoValida e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(IaNoDisponible.class)
	ProblemDetail iaNoDisponible(IaNoDisponible e) {
		ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Ahora no podemos consultar las bases; prueba más tarde");
		p.setProperty("motivo", e.getMessage());
		return p;
	}
}
