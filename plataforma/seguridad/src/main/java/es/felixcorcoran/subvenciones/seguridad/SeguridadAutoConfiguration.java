package es.felixcorcoran.subvenciones.seguridad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * La seguridad de todos los servicios, igual en todos:
 *
 * <ul>
 * <li>Cada servicio es un <b>resource server</b>: valida el JWT que firma Keycloak (firma, emisor y
 * caducidad) con las claves públicas del realm. No guarda sesiones ni cookies.</li>
 * <li>Los roles del realm de Keycloak ({@code realm_access.roles}) pasan a ser roles de Spring
 * ({@code ROLE_admin}).</li>
 * <li>Rutas públicas, de administración y, para lo demás, usuario autenticado
 * ({@link ReglasDeAcceso}). La salud del servicio (actuator/health) es pública.</li>
 * </ul>
 *
 * Sin CSRF: no hay cookies de sesión que un tercero pueda aprovechar, cada petición lleva su token.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableConfigurationProperties(ReglasDeAcceso.class)
public class SeguridadAutoConfiguration {

	public static final String ROL_ADMIN = "admin";

	@Bean
	SecurityFilterChain seguridad(HttpSecurity http, ReglasDeAcceso reglas) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> {
					a.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/health", "/actuator/info").permitAll();
					for (String r : reglas.admin()) a.requestMatchers(coincide(r)).hasRole(ROL_ADMIN);
					for (String r : reglas.publicas()) a.requestMatchers(coincide(r)).permitAll();
					a.anyRequest().authenticated();
				})
				.oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(new RolesDeKeycloak())))
				.httpBasic(b -> b.disable())
				.formLogin(f -> f.disable())
				.logout(l -> l.disable())
				.anonymous(Customizer.withDefaults());
		return http.build();
	}

	private static PathPatternRequestMatcher coincide(String texto) {
		var regla = ReglasDeAcceso.Regla.de(texto);
		var constructor = PathPatternRequestMatcher.withDefaults();
		return regla.metodo() == null ? constructor.matcher(regla.ruta())
				: constructor.matcher(HttpMethod.valueOf(regla.metodo()), regla.ruta());
	}

	/** Los roles del realm de Keycloak como roles de Spring, además de los scopes del token */
	static class RolesDeKeycloak implements Converter<Jwt, AbstractAuthenticationToken> {

		private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

		@Override
		public AbstractAuthenticationToken convert(Jwt jwt) {
			Collection<GrantedAuthority> permisos = new ArrayList<>(scopes.convert(jwt));
			Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
			if (realm != null && realm.get("roles") instanceof List<?> roles) {
				for (Object rol : roles) permisos.add(new SimpleGrantedAuthority("ROLE_" + rol));
			}
			String nombre = jwt.getClaimAsString("preferred_username");
			return new JwtAuthenticationToken(jwt, permisos, nombre != null ? nombre : jwt.getSubject());
		}
	}
}
