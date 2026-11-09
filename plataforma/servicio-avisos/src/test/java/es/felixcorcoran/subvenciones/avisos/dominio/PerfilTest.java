package es.felixcorcoran.subvenciones.avisos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PerfilTest {

	private static final Set<String> PROVINCIAS = Set.of("ES617");

	private static Perfil perfil(String provincia, String forma, int empleados, String antiguedad, String sector) {
		return new Perfil(provincia, " Marbella ", forma, empleados, antiguedad, sector);
	}

	@Test
	void unPerfilDelRadarValeYQuedaLimpio() {
		Perfil p = perfil("ES617", "autonomo", 0, "1a3", "i");
		assertThat(p.datoNoValido(PROVINCIAS)).isEmpty();
		assertThat(p.municipio()).isEqualTo("Marbella");
		assertThat(p.sector()).isEqualTo("I");
	}

	@Test
	void quienAunNoHaEmpezadoNoTienePlantillaNiAntiguedad() {
		Perfil p = perfil("ES617", "emprender", 50, "mas3", "");
		assertThat(p.empleados()).isZero();
		assertThat(p.antiguedad()).isEqualTo("nada");
		assertThat(p.datoNoValido(PROVINCIAS)).isEmpty();
	}

	@Test
	void cadaRespuestaTieneQueSerDeLasDelFormulario() {
		assertThat(perfil("ES999", "autonomo", 0, "1a3", "").datoNoValido(PROVINCIAS)).contains("provincia");
		assertThat(perfil("ES617", "cooperativa", 0, "1a3", "").datoNoValido(PROVINCIAS)).contains("forma");
		assertThat(perfil("ES617", "sociedad", 7, "1a3", "").datoNoValido(PROVINCIAS)).contains("empleados");
		assertThat(perfil("ES617", "sociedad", 1, "nada", "").datoNoValido(PROVINCIAS)).contains("antiguedad");
		assertThat(perfil("ES617", "sociedad", 1, "1a3", "Z").datoNoValido(PROVINCIAS)).contains("sector");
		assertThat(new Perfil("ES617", "x".repeat(81), "autonomo", 0, "1a3", "").datoNoValido(PROVINCIAS)).contains("municipio");
	}
}
