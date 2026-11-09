package es.felixcorcoran.subvenciones.preguntas.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.preguntas.dominio.Citas.CitaPropuesta;
import es.felixcorcoran.subvenciones.preguntas.dominio.Citas.Propuesta;

class CitasTest {

	private static final List<Pasaje> CONTEXTO = List.of(
			new Pasaje("1", 0, 2, "1. Podrán ser beneficiarias las personas físicas que se encuentren en alta en el Régimen\nEspecial de Trabajadores Autónomos (RETA); los clústeres; las coope-\nrativas."),
			new Pasaje("1", 1, 5, "El plazo de presentación de solicitudes se extenderá hasta el día 11 de diciembre de 2026."));

	@Test
	void unaCitaLiteralValeAunqueCambienLosSaltosDeLineaLasMayusculasYLosGuiones() {
		var p = new Propuesta(true, "Sí, pueden pedirla los autónomos.", List.of(
				new CitaPropuesta(1, "«las personas físicas que se encuentren en alta en el Régimen Especial de Trabajadores Autónomos (RETA)»"),
				new CitaPropuesta(1, "LOS CLÚSTERES; LAS COOPERATIVAS")), "m");

		Respuesta r = Citas.comprobar(p, CONTEXTO);

		assertThat(r.consta()).isTrue();
		assertThat(r.citas()).extracting(Respuesta.Cita::pagina).containsExactly(2, 2);
		assertThat(r.citas().getFirst().texto()).startsWith("las personas físicas");
		assertThat(r.citasDescartadas()).isZero();
	}

	@Test
	void unaCitaQueNoEstaTalCualSeDescarta() {
		var p = new Propuesta(true, "El plazo acaba el 11 de diciembre.", List.of(
				new CitaPropuesta(2, "El plazo de presentación de solicitudes se extenderá hasta el día 11 de diciembre de 2026"),
				new CitaPropuesta(2, "El plazo termina el 11 de diciembre de 2026"),
				new CitaPropuesta(1, "hasta el día 11 de diciembre de 2026"),
				new CitaPropuesta(7, "hasta el día 11 de diciembre de 2026"),
				new CitaPropuesta(2, "el plazo")), "m");

		Respuesta r = Citas.comprobar(p, CONTEXTO);

		assertThat(r.consta()).isTrue();
		assertThat(r.citas()).singleElement().extracting(Respuesta.Cita::pagina).isEqualTo(5);
		assertThat(r.citasDescartadas()).isEqualTo(4);
	}

	@Test
	void sinNingunaCitaValidaEsNoConstaDigaLoQueDigaLaIa() {
		var inventada = new Propuesta(true, "Sí, es compatible con el Kit Digital.",
				List.of(new CitaPropuesta(1, "Es compatible con el Kit Digital y otras ayudas")), "m");

		Respuesta r = Citas.comprobar(inventada, CONTEXTO);

		assertThat(r.consta()).isFalse();
		assertThat(r.texto()).isEqualTo(Respuesta.NO_CONSTA);
		assertThat(r.citas()).isEmpty();
		assertThat(r.citasDescartadas()).isEqualTo(1);
	}

	@Test
	void siLaIaDiceQueNoConstaNoSeDaNingunaRespuesta() {
		var p = new Propuesta(false, "Creo que no", List.of(new CitaPropuesta(2, "hasta el día 11 de diciembre de 2026")), "m");
		assertThat(Citas.comprobar(p, CONTEXTO).consta()).isFalse();
	}

	@Test
	void laMismaCitaRepetidaSaleUnaVez() {
		var p = new Propuesta(true, "Hasta el 11 de diciembre.", List.of(
				new CitaPropuesta(2, "hasta el día 11 de diciembre de 2026"),
				new CitaPropuesta(2, "Hasta el día 11 de diciembre de 2026.")), "m");
		assertThat(Citas.comprobar(p, CONTEXTO).citas()).hasSize(1);
	}
}
