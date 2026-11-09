package es.felixcorcoran.subvenciones.preguntas.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TroceadorTest {

	@Test
	void ningunPasajeCruzaDePaginaYCadaUnoSabeLaSuya() {
		String pagina1 = "Base 1. Objeto.\nLas presentes bases regulan las ayudas al comercio local.\n\nBase 2. Beneficiarios.\nPodrán ser beneficiarios los autónomos y las pymes.";
		String pagina2 = "Base 3. Plazo.\nEl plazo de presentación será de diez días hábiles desde la publicación.";

		List<Pasaje> pasajes = Troceador.trocear("1", List.of(pagina1, pagina2));

		assertThat(pasajes).extracting(Pasaje::pagina).containsExactly(1, 2);
		assertThat(pasajes.get(0).texto()).contains("autónomos").doesNotContain("diez días");
		assertThat(pasajes).extracting(Pasaje::orden).containsExactly(0, 1);
	}

	@Test
	void lasPaginasCasiVaciasNoCuentanYUnPdfEscaneadoNoDaPasajes() {
		assertThat(Troceador.trocear("1", List.of("  ", "CSV: 123", "\n"))).isEmpty();
	}

	@Test
	void unaPaginaLargaSeParteEnPasajesDeTamanoManejable() {
		String frase = "Los gastos subvencionables deberán estar efectivamente pagados antes del fin del plazo de justificación. ";
		String pagina = frase.repeat(40);

		List<Pasaje> pasajes = Troceador.trocear("1", List.of(pagina));

		assertThat(pasajes).hasSizeGreaterThan(2);
		assertThat(pasajes).allSatisfy(p -> {
			assertThat(p.texto().length()).isLessThanOrEqualTo(Troceador.MAXIMO);
			assertThat(p.pagina()).isEqualTo(1);
		});
		// No se pierde texto: cada frase sigue entera en algún pasaje
		assertThat(String.join(" ", pasajes.stream().map(Pasaje::texto).toList())).contains(frase.strip());
	}
}
