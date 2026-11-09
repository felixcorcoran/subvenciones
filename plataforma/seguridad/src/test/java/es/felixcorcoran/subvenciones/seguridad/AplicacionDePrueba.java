package es.felixcorcoran.subvenciones.seguridad;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Un servicio de mentira con una ruta pública, una privada y una de administración */
@SpringBootApplication
public class AplicacionDePrueba {

	@RestController
	static class Rutas {
		@GetMapping("/publica")
		String publica() {
			return "pública";
		}

		@GetMapping("/privada")
		String privada(@AuthenticationPrincipal Jwt jwt) {
			return jwt.getClaimAsString("azp");
		}

		@PostMapping("/admin/pasada")
		String admin() {
			return "hecho";
		}
	}
}
