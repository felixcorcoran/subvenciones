package es.felixcorcoran.subvenciones.avisos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeSuscripciones.NoEncontrado;
import es.felixcorcoran.subvenciones.avisos.aplicacion.ServicioDeSuscripciones.PerfilNoValido;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;

class ServicioDeSuscripcionesTest {

	private static final Perfil BAR_EN_MALAGA = new Perfil("ES617", "", "autonomo", 0, "1a3", "I");
	private static final Pattern TOKEN = Pattern.compile("t=([A-Za-z0-9_-]{43})");

	private final EnMemoria memoria = new EnMemoria();
	private Instant ahora = Instant.parse("2026-11-09T08:00:00Z");

	private final ServicioDeSuscripciones servicio = new ServicioDeSuscripciones(memoria, () -> Set.of("ES617", "ES300"),
			new Plantillas(new Plantillas.Direcciones("https://panel", "https://api", "https://radar")), memoria,
			Runnable::run, new Clock() {
				@Override
				public ZoneId getZone() {
					return ZoneId.of("Europe/Madrid");
				}

				@Override
				public Clock withZone(ZoneId zone) {
					return this;
				}

				@Override
				public Instant instant() {
					return ahora;
				}
			});

	private String token() {
		Matcher m = TOKEN.matcher(memoria.buzon.getLast().texto());
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	@Test
	void alApuntarseLlegaUnCorreoParaConfirmarYHastaEntoncesNoEstaActivo() {
		servicio.apuntar(" Ana@Ejemplo.es ", BAR_EN_MALAGA);

		assertThat(memoria.buzon).singleElement().satisfies(m -> {
			assertThat(m.para()).isEqualTo("ana@ejemplo.es");
			assertThat(m.asunto()).isEqualTo("Confirma tus avisos de ayudas");
			assertThat(m.texto()).contains("https://panel/confirmar?t=");
		});
		String t = token();
		assertThat(servicio.ver(t).activo()).isFalse();
		assertThat(servicio.confirmar(t).activo()).isTrue();
		assertThat(servicio.confirmar(t).activo()).isTrue();
	}

	@Test
	void siYaEstaApuntadoNoCambiaNadaYSoloSeLeRecuerdaSuEnlace() {
		servicio.apuntar("ana@ejemplo.es", BAR_EN_MALAGA);
		String t = token();
		servicio.confirmar(t);
		ahora = ahora.plusSeconds(3600);

		servicio.apuntar("ana@ejemplo.es", new Perfil("ES300", "", "sociedad", 10, "mas3", "G"));

		assertThat(memoria.buzon.getLast().asunto()).isEqualTo("Ya recibes los avisos de ayudas");
		assertThat(servicio.ver(t).perfil()).isEqualTo(BAR_EN_MALAGA);
	}

	@Test
	void noSeMandaOtroCorreoDeAltaHastaPasadosDiezMinutos() {
		servicio.apuntar("ana@ejemplo.es", BAR_EN_MALAGA);
		ahora = ahora.plusSeconds(5 * 60);
		servicio.apuntar("ana@ejemplo.es", BAR_EN_MALAGA);
		assertThat(memoria.buzon).hasSize(1);

		ahora = ahora.plusSeconds(6 * 60);
		servicio.apuntar("ana@ejemplo.es", BAR_EN_MALAGA);
		assertThat(memoria.buzon).hasSize(2);
	}

	@Test
	void laBajaLoBorraTodoYUnEnlaceQueNoExisteNoDaNada() {
		servicio.apuntar("ana@ejemplo.es", BAR_EN_MALAGA);
		String t = token();
		servicio.borrar(t);
		assertThat(memoria.suscriptores).isEmpty();
		servicio.borrar(t);
		assertThatThrownBy(() -> servicio.ver(t)).isInstanceOf(NoEncontrado.class);
	}

	@Test
	void unPerfilQueNoEsDelFormularioNoSeAcepta() {
		assertThatThrownBy(() -> servicio.apuntar("ana@ejemplo.es", new Perfil("ES999", "", "autonomo", 0, "1a3", "")))
				.isInstanceOf(PerfilNoValido.class).hasMessageContaining("provincia");
		assertThatThrownBy(() -> servicio.apuntar("ana@ejemplo.es", new Perfil("ES617", "", "sociedad", 7, "1a3", "")))
				.isInstanceOf(PerfilNoValido.class).hasMessageContaining("empleados");
		assertThat(memoria.buzon).isEmpty();
	}
}
