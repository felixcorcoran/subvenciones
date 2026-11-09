package es.felixcorcoran.subvenciones.avisos.adaptadores.salida.encaje;

import java.util.List;
import java.util.Objects;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Encajes;
import es.felixcorcoran.subvenciones.avisos.dominio.Encaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;

/**
 * El servicio de encaje por REST (POST /api/v1/encajes): el mismo perfil que el formulario del
 * radar, y las convocatorias abiertas con su estado y sus motivos. Un fallo de red se reintenta
 * una vez; si sigue fallando, la pasada se para y se repite en la siguiente (nada se marca como
 * enviado sin haberlo enviado).
 */
public class EncajesHttp implements Encajes {

	private final RestClient http;

	public EncajesHttp(RestClient http) {
		this.http = http;
	}

	@Override
	public List<Encaje> para(Perfil perfil) {
		try {
			return pedir(perfil);
		} catch (ResourceAccessException e) {
			return pedir(perfil);
		}
	}

	private List<Encaje> pedir(Perfil perfil) {
		List<Encaje> r = http.post()
				.uri("/api/v1/encajes")
				.contentType(MediaType.APPLICATION_JSON)
				.body(perfil)
				.retrieve()
				.body(new ParameterizedTypeReference<List<Encaje>>() {
				});
		return Objects.requireNonNullElse(r, List.of());
	}
}
