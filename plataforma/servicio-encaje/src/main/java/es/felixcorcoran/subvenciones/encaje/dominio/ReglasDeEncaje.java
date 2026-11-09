package es.felixcorcoran.subvenciones.encaje.dominio;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria.Codigo;
import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria.Respuesta;
import es.felixcorcoran.subvenciones.encaje.dominio.Encaje.Estado;
import es.felixcorcoran.subvenciones.encaje.dominio.Encaje.Motivo;
import es.felixcorcoran.subvenciones.encaje.dominio.Encaje.Tipo;
import es.felixcorcoran.subvenciones.encaje.dominio.Perfil.Forma;

/**
 * ¿Puede este negocio pedir esta ayuda? Las mismas reglas que la web (src/lib/encaje.ts), frase por
 * frase: lo que dice la web, lo que llega por correo y lo que responde este servicio tiene que ser
 * lo mismo. {@code ContratoConLaWebTest} lo comprueba con los casos que calcula el código de la web.
 *
 * <p>Regla de oro: si un dato no se puede saber, la ayuda va a REVISA, nunca a SI.
 */
public final class ReglasDeEncaje {

	private static final String ARTICULOS = "(el|la|los|las|o|a|os|as|l'|es|sa|ses|els|les)";
	private static final Pattern ARTICULO_DETRAS = Pattern.compile("^(.*),\\s*" + ARTICULOS + "$");
	private static final Pattern ARTICULO_DETRAS_I = Pattern.compile("^(.*),\\s*" + ARTICULOS + "$", Pattern.CASE_INSENSITIVE);
	private static final Pattern AYUNTAMIENTO = Pattern.compile("^AYUNTAMIENTO|^AJUNTAMENT|^CONCELLO|^UDALA", Pattern.CASE_INSENSITIVE);
	private static final Pattern PYME = Pattern.compile("PYME|SIN INFORMACION", Pattern.CASE_INSENSITIVE);
	private static final Pattern GRANDE = Pattern.compile("GRAN EMPRESA|SIN INFORMACION", Pattern.CASE_INSENSITIVE);
	private static final Pattern SECCION = Pattern.compile("^[A-V]$", Pattern.CASE_INSENSITIVE);
	private static final Set<String> ENLACES = Set.of("de", "del", "la", "las", "los", "el", "y", "i", "d'", "l'");
	private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
			"septiembre", "octubre", "noviembre", "diciembre" };

	private final Catalogo catalogo;

	public ReglasDeEncaje(Catalogo catalogo) {
		this.catalogo = catalogo;
	}

	public Encaje encaje(Convocatoria c, Perfil p, LocalDate hoy) {
		var m = new Motivos();

		// Plazo
		LocalDate cierre = c.cierre();
		if (cierre != null && cierre.isBefore(hoy)) m.no("El plazo terminó el " + fechaLarga(cierre));

		// Zona (códigos NUTS: ES, ES6, ES61, ES617…)
		List<Codigo> zonas = c.regiones().stream().filter(r -> r.codigo().startsWith("ES")).toList();
		if (zonas.isEmpty()) {
			m.revisa("No indica la zona: comprueba en las bases dónde tiene que estar el negocio");
		} else if (zonas.stream().anyMatch(r -> p.provincia().startsWith(r.codigo()))) {
			m.bien(zonas.stream().anyMatch(r -> r.codigo().equals("ES")) ? "Para toda España" : "Es para tu zona");
		} else {
			m.no("Solo para negocios de " + zonas.stream()
					.map(r -> catalogo.nombreZona(r.codigo(), r.nombre()))
					.collect(Collectors.joining(", ")));
		}

		// Municipio
		String municipio = municipioDe(c);
		if (municipio != null) {
			if (p.municipio().isBlank()) {
				m.revisa("Es de " + municipio + ": normalmente solo para negocios de allí");
			} else {
				String a = normalizarMunicipio(municipio);
				String b = normalizarMunicipio(p.municipio());
				if (a.equals(b) || a.contains(b) || b.contains(a)) m.bien("Es de tu municipio");
				else m.no("Solo para negocios de " + municipio);
			}
		}

		// Tamaño de empresa (tipos de beneficiario de la BDNS)
		int minEmp = p.plantilla().minimo;
		int maxEmp = p.plantilla().maximo;
		boolean pyme = c.beneficiarios().stream().anyMatch(b -> PYME.matcher(b).find());
		boolean grande = c.beneficiarios().stream().anyMatch(b -> GRANDE.matcher(b).find());
		if (minEmp >= 250 && !grande) m.no("Solo para pymes y autónomos");
		if (minEmp < 250 && !pyme && grande) m.no("Solo para grandes empresas");

		// Sector
		Set<String> secciones = new LinkedHashSet<>();
		for (Codigo s : c.sectores()) {
			String letra = catalogo.seccionDe(s.codigo());
			if (letra != null) secciones.add(letra);
		}
		String nombres = secciones.stream().map(catalogo::nombreSeccion).collect(Collectors.joining(", "));
		if (secciones.isEmpty() || secciones.size() >= 8) {
			m.bien("Abierta a cualquier sector");
		} else if (p.sector().isEmpty()) {
			m.revisa("Solo para algunos sectores: " + nombres);
		} else if (secciones.contains(p.sector())) {
			List<Codigo> propios = c.sectores().stream()
					.filter(s -> p.sector().equals(catalogo.seccionDe(s.codigo())))
					.toList();
			if (propios.stream().anyMatch(s -> SECCION.matcher(s.codigo().strip()).matches())) m.bien("Es para tu sector");
			else m.revisa("Solo para parte de tu sector: " + propios.stream().map(Codigo::nombre).collect(Collectors.joining(", ")));
		} else {
			m.no("Es para otro sector: " + nombres);
		}

		// Lo que dicen las bases
		if (c.analisis() == null) m.revisa("Aún no hemos leído las bases: mira los requisitos en la convocatoria");
		else bases(c.analisis(), p, minEmp, maxEmp, m);

		return m.encaje();
	}

	private static void bases(Convocatoria.Analisis ia, Perfil p, int minEmp, int maxEmp, Motivos m) {
		Convocatoria.Condiciones k = ia.condiciones();
		switch (p.forma()) {
			case AUTONOMO -> {
				if (k.autonomos() == Respuesta.NO) m.no("No admite autónomos");
				else if (k.autonomos() == Respuesta.SI) m.bien("Admite autónomos");
			}
			case SOCIEDAD -> {
				if (k.sociedades() == Respuesta.NO) m.no("No admite sociedades");
				else if (k.sociedades() == Respuesta.SI) m.bien("Admite sociedades");
			}
			case EMPRENDER -> {
				int minimo = k.antiguedadMinMeses() == null ? 0 : k.antiguedadMinMeses();
				if (k.emprendedores() == Respuesta.SI) m.bien("Sirve para quien aún no ha empezado");
				else if (k.emprendedores() == Respuesta.NO || minimo > 0) m.no("Hay que tener ya el negocio en marcha");
				else m.revisa("No dice si sirve para quien aún no ha empezado");
			}
		}

		Integer max = k.maxEmpleados();
		if (max != null) {
			if (minEmp > max) m.no("Solo hasta " + max + " " + (max == 1 ? "trabajador" : "trabajadores"));
			else if (maxEmp <= max) m.bien("Tu plantilla entra");
			else m.revisa("Solo hasta " + max + " trabajadores: comprueba tu plantilla");
		}

		if (p.forma() != Forma.EMPRENDER) {
			int minMes = p.antiguedad().minMeses;
			int maxMes = p.antiguedad().maxMeses;
			Integer desde = k.antiguedadMinMeses();
			if (desde != null && desde > 0) {
				if (maxMes < desde) m.no("Pide al menos " + meses(desde) + " de actividad");
				else if (minMes >= desde) m.bien("Llevas el tiempo que pide");
				else m.revisa("Pide al menos " + meses(desde) + " de actividad");
			}
			Integer hasta = k.antiguedadMaxMeses();
			if (hasta != null) {
				if (minMes > hasta) m.no("Solo para negocios de menos de " + meses(hasta));
				else if (maxMes <= hasta) m.bien("Para negocios nuevos, como el tuyo");
				else m.revisa("Solo para negocios de menos de " + meses(hasta));
			}
		}
		if (ia.dudas() != null && !ia.dudas().isEmpty()) m.revisa(ia.dudas());
	}

	/** El municipio al que se limita la ayuda, si se sabe */
	public static String municipioDe(Convocatoria c) {
		if (c.organo().nivel1().equals("LOCAL") && AYUNTAMIENTO.matcher(c.organo().nivel3()).find()) {
			return nombreMunicipio(c.organo().nivel2());
		}
		return c.analisis() == null ? null : c.analisis().condiciones().municipio();
	}

	/** «CARBALLIÑO, O» → «o carballino»; «Málaga» → «malaga» */
	static String normalizarMunicipio(String s) {
		String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
		Matcher m = ARTICULO_DETRAS.matcher(t);
		if (m.matches()) t = m.group(2) + " " + m.group(1);
		return t.replaceAll("[^a-z0-9ñ ]+", " ").replaceAll("\\s+", " ").strip();
	}

	/** «CARBALLIÑO, O» → «O Carballiño», para mostrarlo */
	static String nombreMunicipio(String s) {
		String t = s.strip();
		Matcher m = ARTICULO_DETRAS_I.matcher(t);
		if (m.matches()) t = m.group(2) + " " + m.group(1);
		String[] palabras = t.toLowerCase(Locale.ROOT).split(" ", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < palabras.length; i++) {
			String w = palabras[i];
			if (i > 0) sb.append(' ');
			if ((i > 0 && ENLACES.contains(w)) || w.isEmpty()) sb.append(w);
			else sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
		}
		return sb.toString();
	}

	private static String meses(int n) {
		if (n % 12 == 0) return (n / 12) + " " + (n == 12 ? "año" : "años");
		return n + " " + (n == 1 ? "mes" : "meses");
	}

	/** 2026-12-31 → «31 de diciembre de 2026» */
	static String fechaLarga(LocalDate fecha) {
		return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
	}

	private static final class Motivos {
		private final List<Motivo> lista = new ArrayList<>();

		void bien(String texto) {
			lista.add(new Motivo(Tipo.BIEN, texto));
		}

		void revisa(String texto) {
			lista.add(new Motivo(Tipo.REVISA, texto));
		}

		void no(String texto) {
			lista.add(new Motivo(Tipo.NO, texto));
		}

		Encaje encaje() {
			Estado estado = lista.stream().anyMatch(x -> x.tipo() == Tipo.NO) ? Estado.NO
					: lista.stream().anyMatch(x -> x.tipo() == Tipo.REVISA) ? Estado.REVISA : Estado.SI;
			return new Encaje(estado, lista);
		}
	}
}
