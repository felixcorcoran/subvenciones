package es.felixcorcoran.subvenciones.encaje.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.ConvocatoriaRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.TraductorRadar;
import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria;
import tools.jackson.databind.json.JsonMapper;

/** Lo que se guarda en la columna JSONB vuelve igual: sin perder fechas, condiciones ni dudas */
class DocumentoJsonTest {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Casos(List<ConvocatoriaRadar> convocatorias) {
	}

	@Test
	void cadaConvocatoriaVaYVuelveIgual() throws Exception {
		JsonMapper json = JsonMapper.builder().build();
		Casos casos;
		try (InputStream in = getClass().getResourceAsStream("/contrato/casos-encaje.json")) {
			casos = json.readValue(in, Casos.class);
		}
		for (ConvocatoriaRadar r : casos.convocatorias()) {
			Convocatoria c = TraductorRadar.convocatoria(r);
			assertThat(json.readValue(json.writeValueAsString(c), Convocatoria.class)).isEqualTo(c);
		}
	}
}
