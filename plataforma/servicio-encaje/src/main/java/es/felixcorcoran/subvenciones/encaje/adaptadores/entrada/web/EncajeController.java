package es.felixcorcoran.subvenciones.encaje.adaptadores.entrada.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.ConvocatoriaRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.PerfilRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.TraductorRadar;
import es.felixcorcoran.subvenciones.encaje.aplicacion.PerfilNoValido;
import es.felixcorcoran.subvenciones.encaje.aplicacion.ServicioDeEncaje;
import es.felixcorcoran.subvenciones.encaje.dominio.Encaje;

/**
 * Entrada REST. Las convocatorias llegan en el formato de la web; en la fase 2 llegarán como
 * eventos de Kafka y este endpoint se queda para cargas a mano y pruebas.
 */
@RestController
@RequestMapping("/api/v1")
class EncajeController {

	/** Una convocatoria en la respuesta: lo que necesita una lista, más el encaje */
	record Fila(String codigo, String titulo, LocalDate cierre, Encaje.Estado estado, List<Encaje.Motivo> motivos) {

		static Fila de(ServicioDeEncaje.Resultado r) {
			return new Fila(r.convocatoria().codigo(), r.convocatoria().titulo(), r.convocatoria().cierre(),
					r.encaje().estado(), r.encaje().motivos());
		}
	}

	private final ServicioDeEncaje servicio;

	EncajeController(ServicioDeEncaje servicio) {
		this.servicio = servicio;
	}

	@PutMapping("/convocatorias/{codigo}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void guardar(@PathVariable String codigo, @RequestBody ConvocatoriaRadar convocatoria) {
		if (!codigo.equals(convocatoria.codigo())) {
			throw new IllegalArgumentException("El código de la ruta y el del cuerpo tienen que ser el mismo");
		}
		servicio.registrar(TraductorRadar.convocatoria(convocatoria));
	}

	@PostMapping("/encajes")
	List<Fila> encajes(@RequestBody PerfilRadar perfil) {
		return servicio.encajesPara(TraductorRadar.perfil(perfil)).stream().map(Fila::de).toList();
	}

	@ExceptionHandler(PerfilNoValido.class)
	ProblemDetail perfilNoValido(PerfilNoValido e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problema.setProperty("dato", e.dato());
		return problema;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail peticionIncorrecta(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}
}
