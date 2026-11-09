package es.felixcorcoran.subvenciones.seguridad;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qué rutas de un servicio son públicas y cuáles son de administración, en la configuración de cada
 * servicio. Cada regla es «MÉTODO /ruta» o solo «/ruta» (cualquier método), con los patrones de
 * Spring ({@code /api/v1/suscripciones/**}). Lo que no esté en ninguna lista pide un usuario
 * autenticado: nada queda abierto por olvido.
 *
 * <pre>
 * subvenciones:
 *   seguridad:
 *     publicas: [POST /api/v1/encajes]
 *     admin: [PUT /api/v1/convocatorias/**]
 * </pre>
 *
 * @param publicas sin token: lo que usan la web y los enlaces de los correos
 * @param admin    con un token que tenga el rol «admin» de Keycloak
 */
@ConfigurationProperties("subvenciones.seguridad")
public record ReglasDeAcceso(List<String> publicas, List<String> admin) {

	public ReglasDeAcceso {
		publicas = List.copyOf(Objects.requireNonNullElse(publicas, List.of()));
		admin = List.copyOf(Objects.requireNonNullElse(admin, List.of()));
	}

	/** «POST /api/v1/x» → (POST, /api/v1/x); «/api/v1/x» → (null, /api/v1/x) */
	record Regla(String metodo, String ruta) {
		static Regla de(String texto) {
			String[] partes = texto.strip().split("\\s+", 2);
			return partes.length == 2 ? new Regla(partes[0].toUpperCase(java.util.Locale.ROOT), partes[1]) : new Regla(null, partes[0]);
		}
	}
}
