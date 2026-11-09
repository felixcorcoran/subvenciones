package es.felixcorcoran.subvenciones.avisos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.avisos.dominio.Encaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Novedad;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;

/** La pasada de avisos con todo en memoria y un encaje de mentira */
class ServicioDeAvisosTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final LocalDate HOY = LocalDate.of(2026, 11, 9);
	private static final Perfil BAR_EN_MALAGA = new Perfil("ES617", "", "autonomo", 0, "1a3", "I");
	private static final Pattern TOKEN = Pattern.compile("t=([A-Za-z0-9_-]{43})");

	private final EnMemoria memoria = new EnMemoria();
	/** Lo que responde el encaje para cualquier negocio */
	private final Map<String, Encaje.Estado> encajes = new HashMap<>();
	private final List<Perfil> preguntados = new ArrayList<>();
	private Instant ahora = HOY.atTime(9, 0).atZone(MADRID).toInstant();
	private int maxCorreosDia = 90;

	private ServicioDeSuscripciones suscripciones;
	private ServicioDeAvisos avisos;

	@BeforeEach
	void montar() {
		montarCon(maxCorreosDia);
	}

	private void montarCon(int tope) {
		Clock reloj = new Clock() {
			@Override
			public ZoneId getZone() {
				return MADRID;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return ahora;
			}
		};
		var plantillas = new Plantillas(new Plantillas.Direcciones("https://panel", "https://api", "https://radar"));
		suscripciones = new ServicioDeSuscripciones(memoria, () -> Set.of("ES617", "ES300"), plantillas, memoria,
				Runnable::run, reloj);
		avisos = new ServicioDeAvisos(memoria, memoria, memoria, memoria, perfil -> {
			preguntados.add(perfil);
			return encajes.entrySet().stream()
					.map(e -> new Encaje(e.getKey(), "Ayuda " + e.getKey(), HOY.plusDays(30), e.getValue(),
							List.of(new Encaje.Motivo("bien", "Es para tu zona"))))
					.toList();
		}, memoria, plantillas, suscripciones, memoria, Runnable::run, reloj, tope);
	}

	private String apuntado(String email) {
		suscripciones.apuntar(email, BAR_EN_MALAGA);
		Matcher m = TOKEN.matcher(memoria.buzon.getLast().texto());
		assertThat(m.find()).isTrue();
		suscripciones.confirmar(m.group(1));
		memoria.buzon.clear();
		return m.group(1);
	}

	private void llega(String codigo, String categoria, LocalDate cierre) {
		avisos.alRecibir(UUID.randomUUID(), new Novedad(codigo, "Ayuda " + codigo, categoria, cierre));
	}

	@Test
	void laPrimeraPasadaNoAvisaDeLoQueYaEstabaPublicado() {
		apuntado("ana@ejemplo.es");
		llega("1", "ayuda", null);
		encajes.put("1", Encaje.Estado.SI);

		var r = avisos.pasada();

		assertThat(r.avisos()).isZero();
		assertThat(memoria.buzon).isEmpty();
	}

	@Test
	void unaAyudaNuevaQueEncajaLlegaEnUnSoloCorreoConLaBajaConUnClic() {
		String token = apuntado("ana@ejemplo.es");
		avisos.pasada();
		llega("1", "ayuda", HOY.plusDays(30));
		llega("2", "ayuda", null);
		llega("3", "ayuda", null);
		encajes.put("1", Encaje.Estado.SI);
		encajes.put("2", Encaje.Estado.REVISA);
		encajes.put("3", Encaje.Estado.NO);

		var r = avisos.pasada();

		assertThat(r.nuevas()).isEqualTo(3);
		assertThat(r.avisos()).isEqualTo(2);
		assertThat(r.correos()).isEqualTo(1);
		var correo = memoria.buzon.getFirst();
		assertThat(correo.para()).isEqualTo("ana@ejemplo.es");
		assertThat(correo.asunto()).isEqualTo("2 ayudas nuevas para tu negocio");
		assertThat(correo.texto()).contains("Puedes pedirla: Ayuda 1").contains("Revisa: Ayuda 2").doesNotContain("Ayuda 3");
		assertThat(correo.texto().indexOf("Ayuda 1")).isLessThan(correo.texto().indexOf("Ayuda 2"));
		assertThat(correo.cabeceras()).containsEntry("List-Unsubscribe", "<https://api/api/v1/baja/" + token + ">");

		// No se avisa dos veces de lo mismo
		memoria.buzon.clear();
		assertThat(avisos.pasada().correos()).isZero();
	}

	@Test
	void soloSeAvisaDeAyudasAbiertas() {
		apuntado("ana@ejemplo.es");
		avisos.pasada();
		llega("premio", "premio", null);
		llega("cerrada", "ayuda", HOY.minusDays(1));
		encajes.put("premio", Encaje.Estado.SI);
		encajes.put("cerrada", Encaje.Estado.SI);

		assertThat(avisos.pasada().avisos()).isZero();
		assertThat(memoria.buzon).isEmpty();
	}

	@Test
	void sinNovedadesNoSePreguntaAlEncaje() {
		apuntado("ana@ejemplo.es");
		avisos.pasada();
		preguntados.clear();

		avisos.pasada();

		assertThat(preguntados).isEmpty();
	}

	@Test
	void conElTopeDiarioLoQueNoCabeSaleAlDiaSiguiente() {
		montarCon(1);
		apuntado("ana@ejemplo.es");
		apuntado("luis@ejemplo.es");
		avisos.pasada();
		llega("1", "ayuda", null);
		encajes.put("1", Encaje.Estado.SI);

		var hoy = avisos.pasada();
		assertThat(hoy.correos()).isEqualTo(1);
		assertThat(hoy.paraManana()).isEqualTo(1);

		ahora = ahora.plusSeconds(86_400);
		assertThat(avisos.pasada().correos()).isEqualTo(1);
		assertThat(memoria.buzon).extracting(Puertos.Correo.Mensaje::para)
				.containsExactlyInAnyOrder("ana@ejemplo.es", "luis@ejemplo.es");
	}

	@Test
	void siAlEnviarYaNoEncajaNoSeManda() {
		apuntado("ana@ejemplo.es");
		avisos.pasada();
		llega("1", "ayuda", null);
		encajes.put("1", Encaje.Estado.SI);
		montarCon(0); // hoy no cabe nada
		avisos.pasada();
		montarCon(90);
		encajes.put("1", Encaje.Estado.NO); // mañana ya no encaja (cambió el negocio o la ayuda)

		ahora = ahora.plusSeconds(86_400);
		assertThat(avisos.pasada().correos()).isZero();
		assertThat(memoria.pendientes()).isEmpty();
	}

	@Test
	void elMismoEventoDosVecesSoloCuentaUna() {
		UUID id = UUID.randomUUID();
		assertThat(avisos.alRecibir(id, new Novedad("1", "x", "ayuda", null))).isTrue();
		assertThat(avisos.alRecibir(id, new Novedad("1", "x", "ayuda", null))).isFalse();
	}

	@Test
	void laPasadaBorraLasAltasSinConfirmarDeMasDe48Horas() {
		suscripciones.apuntar("sin@confirmar.es", BAR_EN_MALAGA);
		ahora = ahora.plusSeconds(49 * 3600);

		assertThat(avisos.pasada().sinConfirmarBorradas()).isEqualTo(1);
		assertThat(memoria.suscriptores).isEmpty();
	}
}
