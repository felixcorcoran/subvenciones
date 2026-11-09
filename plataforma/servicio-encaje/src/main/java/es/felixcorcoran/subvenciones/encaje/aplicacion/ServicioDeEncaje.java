package es.felixcorcoran.subvenciones.encaje.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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
	private final ReglasDeEncaje reglas;
	private final Catalogo catalogo;
	private final Clock reloj;

	public ServicioDeEncaje(Convocatorias convocatorias, ReglasDeEncaje reglas, Catalogo catalogo, Clock reloj) {
		this.convocatorias = convocatorias;
		this.reglas = reglas;
		this.catalogo = catalogo;
		this.reloj = reloj;
	}

	public void registrar(Convocatoria convocatoria) {
		convocatorias.guardar(convocatoria);
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
