package es.felixcorcoran.subvenciones.encaje.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import es.felixcorcoran.subvenciones.encaje.dominio.Catalogo;
import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria;
import es.felixcorcoran.subvenciones.encaje.dominio.Encaje;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil;
import es.felixcorcoran.subvenciones.encaje.dominio.ReglasDeEncaje;

/** Casos de uso: tener al día las convocatorias y decir a un negocio cuáles puede pedir */
public class ServicioDeEncaje {

	/** Una convocatoria con su encaje para un negocio */
	public record Resultado(Convocatoria convocatoria, Encaje encaje) {
	}

	private final Convocatorias convocatorias;
	private final EventosProcesados eventos;
	private final ReglasDeEncaje reglas;
	private final Catalogo catalogo;
	private final Clock reloj;

	public ServicioDeEncaje(Convocatorias convocatorias, EventosProcesados eventos, ReglasDeEncaje reglas,
			Catalogo catalogo, Clock reloj) {
		this.convocatorias = convocatorias;
		this.eventos = eventos;
		this.reglas = reglas;
		this.catalogo = catalogo;
		this.reloj = reloj;
	}

	public void registrar(Convocatoria convocatoria) {
		convocatorias.guardar(convocatoria);
	}

	/**
	 * Registra la convocatoria que trae un evento, solo si ese evento no se había aplicado ya. Quien
	 * llama tiene que hacerlo en una transacción, para que el apunte y la convocatoria vayan juntos.
	 *
	 * @return false si el evento estaba repetido y no se ha hecho nada
	 */
	public boolean registrarDesdeEvento(UUID idEvento, Convocatoria convocatoria) {
		if (!eventos.apuntar(idEvento)) return false;
		convocatorias.guardar(convocatoria);
		return true;
	}

	/**
	 * Todas las convocatorias abiertas con su encaje para este negocio: primero las que puede pedir,
	 * luego las que hay que revisar y al final las demás; dentro de cada grupo, las que cierran antes.
	 */
	public List<Resultado> encajesPara(Perfil perfil) {
		perfil.datoNoValido(catalogo).ifPresent(dato -> {
			throw new PerfilNoValido(dato);
		});
		LocalDate hoy = LocalDate.now(reloj);
		return convocatorias.abiertasEl(hoy).stream()
				.map(c -> new Resultado(c, reglas.encaje(c, perfil, hoy)))
				.sorted(Comparator.comparing((Resultado r) -> r.encaje().estado())
						.thenComparing(r -> r.convocatoria().cierre(), Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(r -> r.convocatoria().codigo()))
				.toList();
	}
}
