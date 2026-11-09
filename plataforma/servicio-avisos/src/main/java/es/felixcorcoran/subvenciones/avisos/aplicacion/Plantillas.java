package es.felixcorcoran.subvenciones.avisos.aplicacion;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo.Mensaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Encaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Perfil;

/**
 * Los tres correos, como en alertas: confirmar el alta, «ya estabas apuntado» y el de las ayudas
 * nuevas. HTML sencillo con estilos en línea (lo que entienden todos los gestores de correo) y la
 * misma versión en texto plano.
 */
public class Plantillas {

	/**
	 * @param web   dirección del panel (confirmar, gestionar)
	 * @param api   dirección pública de este servicio (baja con un clic desde el gestor de correo)
	 * @param radar dirección del Radar de subvenciones (fichas de las ayudas)
	 */
	public record Direcciones(String web, String api, String radar) {
	}

	private static final Map<String, String> FORMAS = Map.of("autonomo", "Autónomo o autónoma", "sociedad",
			"Empresa (sociedad)", "emprender", "Aún no ha empezado");
	private static final Map<Integer, String> PLANTILLA = Map.of(0, "solo yo", 1, "de 1 a 9 personas", 10,
			"de 10 a 49 personas", 50, "de 50 a 249 personas", 250, "250 personas o más");
	private static final Map<String, String> ANTIGUEDAD = Map.of("menos1", "menos de un año", "1a3", "de 1 a 3 años",
			"mas3", "más de 3 años");
	private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
			"septiembre", "octubre", "noviembre", "diciembre" };

	private final Direcciones dir;
	private final Puertos.Provincias provincias;

	/** Sin nombres de provincia: en los correos sale el código (pruebas) */
	public Plantillas(Direcciones dir) {
		this(dir, java.util.Set::of);
	}

	public Plantillas(Direcciones dir, Puertos.Provincias provincias) {
		this.dir = dir;
		this.provincias = provincias;
	}

	public Mensaje confirmar(String email, Perfil perfil, String token) {
		String enlace = dir.web() + "/confirmar?t=" + token;
		String asunto = "Confirma tus avisos de ayudas";
		String aviso = "Si no lo has pedido tú, ignora este correo: sin confirmar no te enviaremos nada y borraremos tu dirección en 48 horas.";
		String texto = "Has pedido recibir por email las ayudas nuevas que encajen con tu negocio:\n" + resumen(perfil)
				+ "\n\nPara empezar, confirma tu dirección:\n" + enlace + "\n\n" + aviso + "\n";
		String html = caja(asunto, parrafo("Has pedido recibir por email las ayudas nuevas que encajen con tu negocio:")
				+ parrafoGris(escapar(resumen(perfil))) + boton("Confirmar mis avisos", enlace) + pie(aviso));
		return new Mensaje(email, asunto, html, texto, Map.of());
	}

	public Mensaje yaApuntado(String email, String token) {
		String enlace = dir.web() + "/gestionar?t=" + token;
		String asunto = "Ya recibes los avisos de ayudas";
		String frase = "Alguien (seguramente tú) ha vuelto a apuntar esta dirección a los avisos del Radar de subvenciones. Ya estaba apuntada, así que no hemos cambiado nada.";
		String texto = frase + "\n\nPara cambiar los datos de tu negocio o darte de baja:\n" + enlace + "\n";
		String html = caja(asunto, parrafo(escapar(frase)) + boton("Gestionar mis avisos", enlace));
		return new Mensaje(email, asunto, html, texto, Map.of());
	}

	/** Las ayudas nuevas que encajan, primero las que puede pedir; con la baja con un clic */
	public Mensaje avisos(String email, Perfil perfil, String token, List<Encaje> ayudas, int total) {
		String asunto = ayudas.size() == 1 ? "Una ayuda nueva para tu negocio" : total + " ayudas nuevas para tu negocio";
		String radar = dir.radar() + "/?" + parametros(perfil);
		String gestionar = dir.web() + "/gestionar?t=" + token;
		String baja = dir.api() + "/api/v1/baja/" + token;

		StringBuilder texto = new StringBuilder("Para tu negocio (" + resumen(perfil) + "):\n\n");
		StringBuilder html = new StringBuilder(parrafoGris("Para tu negocio: " + escapar(resumen(perfil))));
		for (Encaje e : ayudas) {
			String ficha = dir.radar() + "/ayudas/" + e.codigo();
			String etiqueta = e.estado() == Encaje.Estado.SI ? "Puedes pedirla" : "Revisa";
			String plazo = e.cierre() == null ? "Sin fecha de cierre confirmada" : "Hasta el " + fechaLarga(e.cierre());
			String motivos = e.motivos().stream().map(Encaje.Motivo::texto).collect(Collectors.joining(" · "));
			texto.append("• ").append(etiqueta).append(": ").append(e.titulo()).append("\n  ").append(plazo)
					.append("\n  ").append(motivos).append("\n  ").append(ficha).append("\n\n");
			html.append("<div style=\"margin:0 0 16px;padding:12px 16px;border:1px solid #E5E5E5;border-radius:8px\">")
					.append("<p style=\"margin:0 0 4px;font-size:13px;color:").append(e.estado() == Encaje.Estado.SI ? "#1A7F37" : "#9A6700")
					.append("\">").append(etiqueta).append("</p>")
					.append("<p style=\"margin:0 0 4px\"><a href=\"").append(escapar(ficha)).append("\" style=\"color:#111\">")
					.append(escapar(e.titulo())).append("</a></p>")
					.append("<p style=\"margin:0;color:#6B6B6B;font-size:13px\">").append(escapar(plazo)).append(" · ")
					.append(escapar(motivos)).append("</p></div>");
		}
		if (total > ayudas.size()) {
			String mas = "Y " + (total - ayudas.size()) + " más en el radar.";
			texto.append(mas).append("\n");
			html.append(parrafo(escapar(mas)));
		}
		texto.append("Todas tus ayudas en el radar: ").append(radar).append("\n\n")
				.append("Cambiar tu negocio o darte de baja: ").append(gestionar).append("\n");
		html.append(boton("Ver todas tus ayudas en el radar", radar))
				.append(pie("Cambiar tu negocio o darte de baja: <a href=\"" + escapar(gestionar) + "\">gestionar mis avisos</a>"));
		Map<String, String> cabeceras = Map.of(
				"List-Unsubscribe", "<" + baja + ">",
				"List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
		return new Mensaje(email, asunto, caja(asunto, html.toString()), texto.toString(), cabeceras);
	}

	/** «Autónomo o autónoma en Málaga · sector I · solo yo · de 1 a 3 años» */
	String resumen(Perfil p) {
		StringBuilder s = new StringBuilder(FORMAS.getOrDefault(p.forma(), p.forma())).append(" en ").append(provincias.nombre(p.provincia()));
		if (!p.municipio().isEmpty()) s.append(" (").append(p.municipio()).append(")");
		if (!p.sector().isEmpty()) s.append(" · sector ").append(p.sector());
		if (!"emprender".equals(p.forma())) {
			s.append(" · ").append(PLANTILLA.getOrDefault(p.empleados(), "")).append(" · ")
					.append(ANTIGUEDAD.getOrDefault(p.antiguedad(), ""));
		}
		return s.toString();
	}

	/** Los mismos parámetros que usa el radar en su dirección */
	static String parametros(Perfil p) {
		return "provincia=" + url(p.provincia()) + "&municipio=" + url(p.municipio()) + "&forma=" + url(p.forma())
				+ "&empleados=" + p.empleados() + "&antiguedad=" + url(p.antiguedad()) + "&sector=" + url(p.sector());
	}

	static String fechaLarga(LocalDate f) {
		return f.getDayOfMonth() + " de " + MESES[f.getMonthValue() - 1] + " de " + f.getYear();
	}

	private static String url(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	static String escapar(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static String caja(String titulo, String cuerpo) {
		return "<!doctype html><html lang=\"es\"><body style=\"margin:0;background:#F7F7F5;font-family:Arial,sans-serif;color:#111\">"
				+ "<div style=\"max-width:560px;margin:0 auto;padding:32px 24px\">"
				+ "<h1 style=\"font-size:20px;margin:0 0 20px\">" + escapar(titulo) + "</h1>" + cuerpo
				+ "</div></body></html>";
	}

	private static String parrafo(String html) {
		return "<p style=\"margin:0 0 12px\">" + html + "</p>";
	}

	private static String parrafoGris(String html) {
		return "<p style=\"margin:0 0 20px;color:#6B6B6B\">" + html + "</p>";
	}

	private static String boton(String texto, String enlace) {
		return "<p style=\"margin:20px 0\"><a href=\"" + escapar(enlace)
				+ "\" style=\"display:inline-block;background:#111;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none\">"
				+ escapar(texto) + "</a></p>";
	}

	private static String pie(String html) {
		return "<p style=\"margin:24px 0 0;color:#6B6B6B;font-size:13px\">" + html + "</p>";
	}
}
