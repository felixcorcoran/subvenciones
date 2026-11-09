package es.felixcorcoran.subvenciones.encaje.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.PerfilRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.radar.TraductorRadar;
import es.felixcorcoran.subvenciones.encaje.adaptadores.salida.catalogo.CatalogoJson;
import es.felixcorcoran.subvenciones.encaje.aplicacion.PerfilNoValido;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil.Antiguedad;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil.Plantilla;
import tools.jackson.databind.json.JsonMapper;

class PerfilTest {

	private final Catalogo catalogo = new CatalogoJson(JsonMapper.builder().build());

	private static Perfil perfil(String provincia, String forma, int empleados, String antiguedad, String sector) {
		return TraductorRadar.perfil(new PerfilRadar(provincia, " Marbella ", forma, empleados, antiguedad, sector));
	}

	@Test
	void unPerfilDeLaWebValeYQuedaLimpio() {
		Perfil p = perfil("ES617", "autonomo", 0, "1a3", "i");
		assertThat(p.datoNoValido(catalogo)).isEmpty();
		assertThat(p.municipio()).isEqualTo("Marbella");
		assertThat(p.sector()).isEqualTo("I");
	}

	@Test
	void quienAunNoHaEmpezadoNoTienePlantillaNiAntiguedad() {
		Perfil p = perfil("ES617", "emprender", 50, "mas3", "");
		assertThat(p.plantilla()).isEqualTo(Plantilla.SOLO);
		assertThat(p.antiguedad()).isEqualTo(Antiguedad.NADA);
		assertThat(p.datoNoValido(catalogo)).isEmpty();
	}

	@Test
	void cadaRespuestaTieneQueSerDeLasDelFormulario() {
		assertThat(perfil("ES999", "autonomo", 0, "1a3", "").datoNoValido(catalogo)).contains("provincia");
		assertThat(perfil("ES617", "sociedad", 1, "nada", "").datoNoValido(catalogo)).contains("antiguedad");
		assertThat(perfil("ES617", "sociedad", 1, "1a3", "Z").datoNoValido(catalogo)).contains("sector");
		assertThat(TraductorRadar.perfil(new PerfilRadar("ES617", "x".repeat(81), "autonomo", 0, "1a3", ""))
				.datoNoValido(catalogo)).contains("municipio");

		assertThatThrownBy(() -> perfil("ES617", "cooperativa", 0, "1a3", ""))
				.isInstanceOf(PerfilNoValido.class).hasMessageContaining("forma");
		assertThatThrownBy(() -> perfil("ES617", "sociedad", 7, "1a3", ""))
				.isInstanceOf(PerfilNoValido.class).hasMessageContaining("empleados");
	}
}
