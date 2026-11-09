package es.felixcorcoran.subvenciones.encaje.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.ConvocatoriaRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.PerfilRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.TraductorRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.salida.catalogo.CatalogoJson;
import es.felixcorcoran.subvenciones.encaje.dominio.Encaje.Motivo;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato con la web: las reglas en Java tienen que dar el mismo estado y los mismos motivos, en
 * el mismo orden, que src/lib/encaje.ts. Los casos (contrato/casos-encaje.json) los calcula el
 * código de la web con convocatorias reales y de prueba y 60 negocios distintos. Si cambia el
 * encaje de la web, se regeneran con la herramienta de alertas
 * ({@code npx tsx herramientas/casos-encaje.mts ../subvenciones}).
 */
class ContratoConLaWebTest {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MotivoEsperado(String tipo, String texto) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Caso(int c, int p, String hoy, String estado, List<MotivoEsperado> motivos) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Casos(List<ConvocatoriaRadar> convocatorias, List<PerfilRadar> perfiles, List<Caso> casos) {
	}

	private static Casos casos;
	private static List<Convocatoria> convocatorias;
	private static List<Perfil> perfiles;
	private static ReglasDeEncaje reglas;

	@BeforeAll
	static void cargar() throws Exception {
		JsonMapper json = JsonMapper.builder().build();
		try (InputStream in = ContratoConLaWebTest.class.getResourceAsStream("/contrato/casos-encaje.json")) {
			casos = json.readValue(in, Casos.class);
		}
		convocatorias = casos.convocatorias().stream().map(TraductorRadar::convocatoria).toList();
		perfiles = casos.perfiles().stream().map(TraductorRadar::perfil).toList();
		reglas = new ReglasDeEncaje(new CatalogoJson(json));
	}

	@Test
	void hayMilesDeCasosDeLosTresEstados() {
		assertThat(casos.casos()).hasSizeGreaterThan(1000);
		assertThat(casos.casos()).extracting(Caso::estado).contains("si", "revisa", "no");
	}

	@Test
	void mismoEstadoYMismosMotivosQueLaWeb() {
		for (Caso caso : casos.casos()) {
			Convocatoria c = convocatorias.get(caso.c());
			Perfil p = perfiles.get(caso.p());
			Encaje e = reglas.encaje(c, p, LocalDate.parse(caso.hoy()));

			List<Motivo> esperados = caso.motivos().stream()
					.map(m -> new Motivo(Encaje.Tipo.valueOf(m.tipo().toUpperCase(Locale.ROOT)), m.texto()))
					.toList();
			assertThat(e.motivos()).as("convocatoria %s, perfil %s, %s", c.codigo(), p, caso.hoy()).isEqualTo(esperados);
			assertThat(e.estado()).isEqualTo(Encaje.Estado.valueOf(caso.estado().toUpperCase(Locale.ROOT)));
		}
	}
}
