package es.felixcorcoran.subvenciones.encaje.dominio;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * El negocio, con las mismas respuestas que el formulario del radar.
 *
 * @param provincia código NUTS3 de la provincia o isla (ES617 = Málaga)
 * @param municipio opcional; vacío si no se sabe
 * @param sector    letra de la sección CNAE-2025 (A–V); vacío si no se sabe
 */
public record Perfil(String provincia, String municipio, Forma forma, Plantilla plantilla, Antiguedad antiguedad,
		String sector) {

	public Perfil {
		Objects.requireNonNull(forma, "forma");
		Objects.requireNonNull(plantilla, "plantilla");
		Objects.requireNonNull(antiguedad, "antiguedad");
		municipio = Objects.requireNonNullElse(municipio, "").strip();
		sector = Objects.requireNonNullElse(sector, "").strip().toUpperCase(Locale.ROOT);
		// Quien aún no ha empezado no tiene plantilla ni antigüedad
		if (forma == Forma.EMPRENDER) {
			plantilla = Plantilla.SOLO;
			antiguedad = Antiguedad.NADA;
		}
	}

	/** El primer dato que no vale, si lo hay */
	public Optional<String> datoNoValido(Catalogo catalogo) {
		if (provincia == null || !catalogo.esProvincia(provincia)) return Optional.of("provincia");
		if (municipio.length() > 80) return Optional.of("municipio");
		if (antiguedad == Antiguedad.NADA && forma != Forma.EMPRENDER) return Optional.of("antiguedad");
		if (!sector.isEmpty() && (sector.length() != 1 || sector.charAt(0) < 'A' || sector.charAt(0) > 'V')) {
			return Optional.of("sector");
		}
		return Optional.empty();
	}

	public enum Forma {
		AUTONOMO, SOCIEDAD, EMPRENDER
	}

	/** Tramos de plantilla, como los pregunta el formulario */
	public enum Plantilla {
		SOLO(0, 0), DE_1_A_9(1, 9), DE_10_A_49(10, 49), DE_50_A_249(50, 249), DESDE_250(250, Integer.MAX_VALUE);

		public final int minimo;
		public final int maximo;

		Plantilla(int minimo, int maximo) {
			this.minimo = minimo;
			this.maximo = maximo;
		}
	}

	/** Meses de actividad de cada respuesta del formulario */
	public enum Antiguedad {
		NADA(0, 0), MENOS_DE_1_ANIO(0, 11), DE_1_A_3_ANIOS(12, 36), MAS_DE_3_ANIOS(37, Integer.MAX_VALUE);

		public final int minMeses;
		public final int maxMeses;

		Antiguedad(int minMeses, int maxMeses) {
			this.minMeses = minMeses;
			this.maxMeses = maxMeses;
		}
	}
}
