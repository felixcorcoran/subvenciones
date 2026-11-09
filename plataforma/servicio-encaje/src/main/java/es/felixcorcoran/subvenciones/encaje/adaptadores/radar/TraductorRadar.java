package es.felixcorcoran.subvenciones.encaje.adaptadores.radar;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import es.felixcorcoran.subvenciones.encaje.aplicacion.PerfilNoValido;
import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria;
import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria.Respuesta;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil.Antiguedad;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil.Forma;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil.Plantilla;

/** Capa anticorrupción: del formato de la web al modelo del dominio */
public final class TraductorRadar {

	private TraductorRadar() {
	}

	public static Convocatoria convocatoria(ConvocatoriaRadar c) {
		ConvocatoriaRadar.Organo o = c.organo();
		return new Convocatoria(
				c.codigo(),
				c.titulo(),
				o == null ? null : new Convocatoria.Organo(o.nivel1(), o.nivel2(), o.nivel3()),
				c.beneficiarios(),
				codigos(c.sectores()),
				codigos(c.regiones()),
				fecha(c.fin()),
				c.ia() == null ? null : analisis(c.ia()));
	}

	/** @throws PerfilNoValido con el dato de la web que no es una de las respuestas del formulario */
	public static Perfil perfil(PerfilRadar p) {
		return new Perfil(p.provincia(), p.municipio(), forma(p.forma()), plantilla(p.empleados(), p.forma()),
				antiguedad(p.antiguedad(), p.forma()), p.sector());
	}

	private static Convocatoria.Analisis analisis(ConvocatoriaRadar.Analisis a) {
		ConvocatoriaRadar.Condiciones k = a.condiciones();
		Convocatoria.Condiciones condiciones = k == null ? Convocatoria.Condiciones.NO_CONSTAN
				: new Convocatoria.Condiciones(respuesta(k.autonomos()), respuesta(k.sociedades()),
						respuesta(k.emprendedores()), k.maxEmpleados(), k.antiguedadMinMeses(), k.antiguedadMaxMeses(),
						k.municipio());
		return new Convocatoria.Analisis(a.categoria(), a.resumen(), a.importe(), a.plazo(), fecha(a.plazoFin()),
				condiciones, a.dudas());
	}

	private static List<Convocatoria.Codigo> codigos(List<ConvocatoriaRadar.Codigo> codigos) {
		return Objects.requireNonNullElse(codigos, List.<ConvocatoriaRadar.Codigo>of()).stream()
				.map(c -> new Convocatoria.Codigo(c.codigo(), c.nombre()))
				.toList();
	}

	private static Respuesta respuesta(String valor) {
		return switch (valor == null ? "" : valor) {
			case "si" -> Respuesta.SI;
			case "no" -> Respuesta.NO;
			default -> Respuesta.NO_CONSTA;
		};
	}

	private static LocalDate fecha(String iso) {
		return iso == null || iso.isBlank() ? null : LocalDate.parse(iso);
	}

	private static Forma forma(String forma) {
		return switch (forma == null ? "" : forma) {
			case "autonomo" -> Forma.AUTONOMO;
			case "sociedad" -> Forma.SOCIEDAD;
			case "emprender" -> Forma.EMPRENDER;
			default -> throw new PerfilNoValido("forma");
		};
	}

	private static Plantilla plantilla(Integer empleados, String forma) {
		if ("emprender".equals(forma)) return Plantilla.SOLO;
		return switch (empleados == null ? -1 : empleados) {
			case 0 -> Plantilla.SOLO;
			case 1 -> Plantilla.DE_1_A_9;
			case 10 -> Plantilla.DE_10_A_49;
			case 50 -> Plantilla.DE_50_A_249;
			case 250 -> Plantilla.DESDE_250;
			default -> throw new PerfilNoValido("empleados");
		};
	}

	private static Antiguedad antiguedad(String antiguedad, String forma) {
		if ("emprender".equals(forma)) return Antiguedad.NADA;
		return switch (antiguedad == null ? "" : antiguedad) {
			case "nada" -> Antiguedad.NADA;
			case "menos1" -> Antiguedad.MENOS_DE_1_ANIO;
			case "1a3" -> Antiguedad.DE_1_A_3_ANIOS;
			case "mas3" -> Antiguedad.MAS_DE_3_ANIOS;
			default -> throw new PerfilNoValido("antiguedad");
		};
	}
}
