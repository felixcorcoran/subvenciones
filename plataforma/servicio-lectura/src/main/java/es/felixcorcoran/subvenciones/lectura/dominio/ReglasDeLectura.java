package es.felixcorcoran.subvenciones.lectura.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import es.felixcorcoran.subvenciones.lectura.dominio.Lectura.CierreDescartado;

/**
 * Las reglas de la lectura, las mismas que scripts/lectura.ts y scripts/analizar.ts de la web:
 * qué se lee, cuándo se reintenta y qué fecha de cierre de las bases se puede usar.
 */
public final class ReglasDeLectura {

	/** Intentos fallidos seguidos antes de esperar */
	public static final int MAX_INTENTOS = 3;
	/** Tras agotar los intentos, días hasta volver a probar: nada se queda bloqueado para siempre */
	public static final int DIAS_ESPERA = 14;
	/** Días desde el registro a partir de los cuales una sin fecha de cierre está «por verificar» */
	static final int DIAS_POR_VERIFICAR = 120;

	private static final Set<String> PLAZO = Set.of("inicio", "fin", "textoInicio", "textoFin");
	private static final Pattern PDF = Pattern.compile("\\.pdf$", Pattern.CASE_INSENSITIVE);
	private static final Pattern DUDAS_DE_PLAZO = Pattern.compile("plazo|fecha|cierre|solicitud", Pattern.CASE_INSENSITIVE);
	private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
			"septiembre", "octubre", "noviembre", "diciembre" };

	private ReglasDeLectura() {
	}

	/** Si toca leerla (o releerla) hoy */
	public static boolean porLeer(Lectura l, LocalDate hoy) {
		Convocatoria c = l.convocatoria();
		if (l.cerrada() || c.documento() == null) return false;
		if (l.ia() != null && l.revisar() == null) return false;
		// Una «por verificar» sin leer se queda así: leerla ahora la haría nueva para los avisos, que
		// avisarían de ella como si siguiera abierta
		if (l.ia() == null && porVerificar(c, hoy)) return false;
		if (l.intentos() < MAX_INTENTOS) return true;
		return l.intentadoEl() == null || !l.intentadoEl().isAfter(hoy.minusDays(DIAS_ESPERA));
	}

	/** Sin fecha de cierre, con el plazo ya empezado (o sin fecha de inicio) y registrada hace más de cuatro meses */
	static boolean porVerificar(Convocatoria c, LocalDate hoy) {
		if (c.fin() != null) return false;
		if (c.inicio() != null && c.inicio().isAfter(hoy)) return false;
		return c.publicada().isBefore(hoy.minusDays(DIAS_POR_VERIFICAR));
	}

	/** Solo se leen PDF: lo demás es un fallo de la convocatoria, no de la consulta */
	public static boolean esPdf(Convocatoria.Documento d) {
		return d != null && d.nombre() != null && PDF.matcher(d.nombre()).find();
	}

	/**
	 * Su ficha ha cambiado: si cambia el documento o el plazo, la IA la vuelve a leer y, mientras,
	 * se mantiene lo leído. Un documento nuevo merece otra oportunidad aunque el anterior fallara.
	 * Con los datos nuevos de la BDNS se vuelve a revisar la fecha de cierre de las bases.
	 */
	public static Lectura alCambiar(Lectura l, Convocatoria nueva, List<String> campos) {
		Lectura r = l.con(nueva);
		boolean documento = campos.contains("documento");
		if (r.ia() != null) {
			String motivo = documento ? "documento nuevo" : campos.stream().anyMatch(PLAZO::contains) ? "cambió el plazo" : null;
			if (motivo != null) r = r.conRevisar(motivo, 0, null, null);
			return revisarCierre(r);
		}
		if (documento) r = r.conRevisar(null, 0, null, null);
		return r;
	}

	/** Lo leído, con la fecha de cierre solo si se puede usar */
	public static Lectura leida(Lectura l, Analisis ia, String modelo) {
		return revisarCierre(l.conIa(ia.conPlazoFinValido(), modelo, null));
	}

	/** Si ya estaba leída (relectura), lo leído antes se queda */
	public static Lectura fallo(Lectura l, String error, LocalDate hoy) {
		String breve = error == null ? "error" : error.substring(0, Math.min(300, error.length()));
		return l.conFallo(l.intentos() + 1, hoy, breve);
	}

	/**
	 * Deja en ia.plazoFin solo una fecha de las bases que se pueda usar; la descartada se guarda
	 * con el motivo. Así quien lea los datos usa la fecha buena sin repetir esta regla.
	 */
	static Lectura revisarCierre(Lectura l) {
		String motivo = motivoCierreDudoso(l.convocatoria(), l.ia());
		if (motivo == null) return l;
		var descartado = new CierreDescartado(l.ia().plazoFin(), motivo);
		return l.conIa(l.ia().conPlazoFin(null), l.modelo(), descartado).conRevisar(l.revisar(), l.intentos(),
				l.intentadoEl(), l.error());
	}

	/**
	 * Por qué no vale la fecha de cierre que sacó la IA, o null si vale. Solo se acepta si no
	 * contradice a la BDNS y es coherente con lo que la propia lectura dice del plazo: una fecha
	 * dudosa no cierra una convocatoria. La excepción es un plazo previo al periodo de la BDNS.
	 */
	static String motivoCierreDudoso(Convocatoria c, Analisis ia) {
		if (ia == null || ia.plazoFin() == null) return null;
		LocalDate fecha;
		try {
			fecha = LocalDate.parse(ia.plazoFin());
		} catch (RuntimeException e) {
			return "no es una fecha";
		}
		if (plazoPrevio(c, ia, fecha)) return null;
		// Con fecha en la BDNS, manda la BDNS: si coincide, está confirmada
		if (c.fin() != null) return c.fin().equals(fecha) ? null : "no coincide con la de la BDNS (" + c.fin() + ")";
		if (fecha.isBefore(c.publicada())) return "es anterior al registro en la BDNS";
		if (c.inicio() != null && fecha.isBefore(c.inicio())) {
			return "es anterior a la apertura del plazo en la BDNS (" + c.inicio() + ")";
		}
		if (!mencionaFecha(ia.plazo() == null ? "" : ia.plazo(), fecha)) return "el plazo leído en las bases no nombra esa fecha";
		if (ia.dudas() != null && DUDAS_DE_PLAZO.matcher(ia.dudas()).find()) {
			return "la lectura de las bases tiene dudas sobre el plazo";
		}
		return null;
	}

	/**
	 * Plazo previo al periodo de la BDNS (src/lib/fechas.ts): la fecha de las bases es anterior al
	 * inicio de la BDNS, no es anterior al registro y el texto del plazo la nombra.
	 */
	static boolean plazoPrevio(Convocatoria c, Analisis ia, LocalDate fecha) {
		return c.inicio() != null && fecha.isBefore(c.inicio()) && !fecha.isBefore(c.publicada())
				&& mencionaFecha(ia.plazo() == null ? "" : ia.plazo(), fecha);
	}

	/** Si el texto nombra esa fecha («31 de enero», «31/01/2027»…) */
	static boolean mencionaFecha(String texto, LocalDate fecha) {
		int dia = fecha.getDayOfMonth(), mes = fecha.getMonthValue();
		var larga = Pattern.compile("(^|\\D)0?" + dia + "\\s+de\\s+" + MESES[mes - 1], Pattern.CASE_INSENSITIVE);
		var corta = Pattern.compile("(^|\\D)0?" + dia + "[/.-]0?" + mes + "([/.-]|\\D|$)");
		return larga.matcher(texto).find() || corta.matcher(texto).find();
	}

	/** Lo que se le cuenta a la IA junto al documento */
	public static String contexto(Convocatoria c) {
		var organo = c.organo() == null ? Stream.<String>empty()
				: Stream.of(c.organo().nivel3(), c.organo().nivel2()).filter(s -> s != null && !s.isBlank());
		return Stream.of(
				"Convocatoria BDNS " + c.codigo() + ": " + c.titulo(),
				"Convoca: " + organo.collect(Collectors.joining(" — ")),
				"Registrada en la BDNS el " + c.publicada() + ".",
				c.fin() == null ? null : "Fin de solicitud según la BDNS: " + c.fin() + ".",
				c.textoInicio() == null ? null : "Inicio del plazo (texto de la BDNS): " + c.textoInicio(),
				c.textoFin() == null ? null : "Fin del plazo (texto de la BDNS): " + c.textoFin(),
				"Explica el documento adjunto siguiendo las reglas.")
				.filter(s -> s != null && !s.isBlank())
				.collect(Collectors.joining("\n"));
	}
}
