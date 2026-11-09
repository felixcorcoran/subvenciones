package es.felixcorcoran.subvenciones.avisos.adaptadores.entrada.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeAvisos;
import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeSuscripciones;
import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeSuscripciones.NoEncontrado;
import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeSuscripciones.PerfilNoValido;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;
import es.felixcorcoran.subvenciones.avisos.dominio.Suscriptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * La API de las suscripciones, la misma que la de alertas. El token de cada persona viaja en sus
 * enlaces y es lo único que da acceso a sus datos.
 */
@RestController
@RequestMapping("/api/v1")
class SuscripcionController {

	private static final String TOKEN = "[A-Za-z0-9_-]{43}";

	record Alta(@NotBlank @Email @Size(max = 254) String email, @NotNull Perfil perfil,
			/** Ha marcado «He leído la política de privacidad» */
			@AssertTrue boolean acepto) {
	}

	record Token(@NotBlank @Pattern(regexp = TOKEN) String token) {
	}

	record Vista(String email, Perfil perfil, boolean confirmado) {
		static Vista de(Suscriptor s) {
			return new Vista(s.email(), s.perfil(), s.activo());
		}
	}

	private final ServicioDeSuscripciones suscripciones;
	private final ServicioDeAvisos avisos;

	SuscripcionController(ServicioDeSuscripciones suscripciones, ServicioDeAvisos avisos) {
		this.suscripciones = suscripciones;
		this.avisos = avisos;
	}

	/** Siempre la misma respuesta, esté o no apuntado */
	@PostMapping("/suscripciones")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void apuntar(@Valid @RequestBody Alta alta) {
		suscripciones.apuntar(alta.email(), alta.perfil());
	}

	@PostMapping("/suscripciones/confirmar")
	Vista confirmar(@Valid @RequestBody Token t) {
		return Vista.de(suscripciones.confirmar(t.token()));
	}

	@GetMapping("/suscripciones/{token:" + TOKEN + "}")
	Vista ver(@PathVariable String token) {
		return Vista.de(suscripciones.ver(token));
	}

	@PutMapping("/suscripciones/{token:" + TOKEN + "}")
	Vista cambiar(@PathVariable String token, @RequestBody Perfil perfil) {
		return Vista.de(suscripciones.cambiar(token, perfil));
	}

	@DeleteMapping("/suscripciones/{token:" + TOKEN + "}")
	ResponseEntity<Void> borrar(@PathVariable String token) {
		suscripciones.borrar(token);
		return ResponseEntity.noContent().build();
	}

	/** Baja con un clic desde el gestor de correo (cabecera List-Unsubscribe-Post, RFC 8058) */
	@PostMapping("/baja/{token:" + TOKEN + "}")
	ResponseEntity<Void> bajaUnClic(@PathVariable String token) {
		suscripciones.borrar(token);
		return ResponseEntity.ok().build();
	}

	/** La pasada a mano. En la fase 4 quedará solo para administradores (OAuth2) */
	@PostMapping("/avisos/pasada")
	ServicioDeAvisos.Resumen pasada() {
		return avisos.pasada();
	}

	@ExceptionHandler(NoEncontrado.class)
	ProblemDetail noEncontrado() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "enlace");
	}

	@ExceptionHandler(PerfilNoValido.class)
	ProblemDetail perfilNoValido(PerfilNoValido e) {
		ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		p.setProperty("dato", e.dato());
		return p;
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail enMarcha(IllegalStateException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}
}
