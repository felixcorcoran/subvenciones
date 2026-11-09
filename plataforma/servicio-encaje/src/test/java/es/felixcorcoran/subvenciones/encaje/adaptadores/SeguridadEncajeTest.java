package es.felixcorcoran.subvenciones.encaje.adaptadores;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Las reglas de acceso del encaje: el encaje es público; cargar convocatorias a mano, solo admin */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class SeguridadEncajeTest extends PruebaConContenedores {

	private static final String CONVOCATORIA = """
			{"codigo":"S1","titulo":"Ayuda","regiones":[{"codigo":"ES61","nombre":"Andalucía"}],"fin":null}""";

	@Autowired
	private WebApplicationContext contexto;

	@Test
	void cargarUnaConvocatoriaAManoEsSoloParaAdministracion() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(SecurityMockMvcConfigurers.springSecurity()).build();
		var cargar = put("/api/v1/convocatorias/S1").contentType(MediaType.APPLICATION_JSON).content(CONVOCATORIA);

		mvc.perform(cargar).andExpect(status().isUnauthorized());
		mvc.perform(cargar.with(jwt())).andExpect(status().isForbidden());
		mvc.perform(cargar.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))).andExpect(status().isNoContent());
	}

	@Test
	void elEncajeNoPideToken() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(SecurityMockMvcConfigurers.springSecurity()).build();
		mvc.perform(post("/api/v1/encajes").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provincia\":\"ES617\",\"municipio\":\"\",\"forma\":\"autonomo\",\"empleados\":0,\"antiguedad\":\"1a3\",\"sector\":\"I\"}"))
				.andExpect(status().isOk());
	}
}
