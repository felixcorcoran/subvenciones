package es.felixcorcoran.subvenciones.recogida.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Cambio;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Documento;
import es.felixcorcoran.subvenciones.recogida.dominio.Ficha.DocumentoFicha;

/**
 * Las reglas de la recogida, las mismas que scripts/recoger.ts y scripts/fichas.ts de la web:
 *
 * <ul>
 * <li>Se descartan las de concesión directa (van a una entidad concreta) y las ya cerradas.</li>
 * <li>Al volver a leer una ficha no se pierde nada: si deja de traer un dato que antes traía, se
 * conserva el anterior.</li>
 * <li>Solo se retiran las que han cerrado con la fecha de la BDNS. Un fallo de consulta nunca
 * cierra ni borra nada (eso lo garantiza el servicio: sin ficha no se toca la convocatoria).</li>
 * </ul>
 */
public final class ReglasDeRecogida {

	/** Días desde el registro a partir de los cuales una sin fecha de cierre está «por verificar» */
	public static final int DIAS_POR_VERIFICAR = 120;
	/** Cuántos cambios se guardan de cada convocatoria */
	static final int MAX_CAMBIOS = 10;

	private static final Pattern CONCESION_DIRECTA = Pattern.compile("^concesi[oó]n directa", Pattern.CASE_INSENSITIVE);
	private static final Pattern EN_CASTELLANO = Pattern.compile("español|castellano", Pattern.CASE_INSENSITIVE);
	private static final String ENLACE_FICHA = "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/";

	private ReglasDeRecogida() {
	}

	/** Por qué no se guarda una convocatoria nueva, si no se guarda */
	public static Optional<String> motivoDescarte(Ficha f, LocalDate hoy) {
		if (f.tipo() != null && CONCESION_DIRECTA.matcher(f.tipo()).find()) return Optional.of("concesión directa");
		if (f.fin() != null && f.fin().isBefore(hoy)) return Optional.of("cerrada");
		return Optional.empty();
	}

	public static Convocatoria aConvocatoria(Ficha f, LocalDate hoy) {
		return new Convocatoria(f.codigo(), f.titulo(), f.organo(), f.sede(), f.publicada(), f.tipo(), f.presupuesto(),
				f.beneficiarios(), f.sectores(), f.regiones(), f.finalidad(), f.inicio(), f.fin(), f.textoInicio(),
				f.textoFin(), f.bases(), elegirDocumento(f, null), idsDocumentos(f), ENLACE_FICHA + f.codigo(), hoy,
				List.of());
	}

	/** Lo que queda tras leer otra vez su ficha, qué ha cambiado y qué se ha conservado */
	public record Actualizacion(Convocatoria convocatoria, List<String> campos, List<String> conservados) {
	}

	/** La guardada con lo que dice su ficha de hoy; no modifica la guardada */
	public static Actualizacion actualizar(Convocatoria g, Ficha f, LocalDate hoy) {
		var cmp = new Comparador();
		String titulo = cmp.elegir("titulo", g.titulo(), f.titulo());
		String sede = cmp.elegir("sede", g.sede(), f.sede());
		String tipo = cmp.elegir("tipo", g.tipo(), f.tipo());
		BigDecimal presupuesto = cmp.elegir("presupuesto", g.presupuesto(), f.presupuesto());
		var beneficiarios = cmp.elegir("beneficiarios", g.beneficiarios(), f.beneficiarios());
		var sectores = cmp.elegir("sectores", g.sectores(), f.sectores());
		var regiones = cmp.elegir("regiones", g.regiones(), f.regiones());
		String finalidad = cmp.elegir("finalidad", g.finalidad(), f.finalidad());
		LocalDate inicio = cmp.elegir("inicio", g.inicio(), f.inicio());
		LocalDate fin = cmp.elegir("fin", g.fin(), f.fin());
		String textoInicio = cmp.elegir("textoInicio", g.textoInicio(), f.textoInicio());
		String textoFin = cmp.elegir("textoFin", g.textoFin(), f.textoFin());
		String bases = cmp.elegir("bases", g.bases(), f.bases());

		Documento documento = g.documento();
		Documento elegido = elegirDocumento(f, g.documento());
		if (elegido != null && (g.documento() == null || elegido.id() != g.documento().id())) {
			documento = elegido;
			cmp.campos.add("documento");
		} else if (elegido == null && g.documento() != null) {
			cmp.conservados.add("documento");
		}

		// Los documentos que cuelgan de la ficha. La primera vez solo se apuntan: no hay con qué comparar
		List<Long> docs = g.docs();
		List<Long> ahora = idsDocumentos(f);
		if (!ahora.isEmpty()) {
			if (!g.docs().isEmpty() && !g.docs().equals(ahora)) cmp.campos.add("documentos");
			docs = ahora;
		}

		List<Cambio> cambios = g.cambios();
		if (!cmp.campos.isEmpty()) {
			var todos = new ArrayList<>(g.cambios());
			todos.add(new Cambio(hoy, cmp.campos));
			cambios = todos.subList(Math.max(0, todos.size() - MAX_CAMBIOS), todos.size());
		}

		var c = new Convocatoria(g.codigo(), titulo, g.organo(), sede, g.publicada(), tipo, presupuesto, beneficiarios,
				sectores, regiones, finalidad, inicio, fin, textoInicio, textoFin, bases, documento, docs, g.enlace(),
				hoy, cambios);
		return new Actualizacion(c, List.copyOf(cmp.campos), List.copyOf(cmp.conservados));
	}

	/** Ha cerrado según la BDNS. La fecha que la IA saque de las bases no cuenta aquí */
	public static boolean cerrada(Convocatoria c, LocalDate hoy) {
		return c.fin() != null && c.fin().isBefore(hoy);
	}

	/** Sin fecha de cierre y registrada hace más de cuatro meses: puede que ya no esté abierta */
	public static boolean porVerificar(Convocatoria c, LocalDate hoy) {
		return c.fin() == null && c.publicada().isBefore(hoy.minusDays(DIAS_POR_VERIFICAR));
	}

	/** Si hoy toca volver a pedir su ficha: una vez al día, o una por semana si está por verificar */
	public static boolean tocaComprobar(Convocatoria c, LocalDate hoy) {
		if (hoy.equals(c.comprobada())) return false;
		return !porVerificar(c, hoy) || c.comprobada() == null || !c.comprobada().isAfter(hoy.minusDays(7));
	}

	/** El documento de las bases: el que ya se usaba si sigue en la ficha; si no, el que está en castellano o el primero */
	static Documento elegirDocumento(Ficha f, Documento anterior) {
		DocumentoFicha doc = null;
		if (anterior != null) doc = f.documentos().stream().filter(d -> d.id() == anterior.id()).findFirst().orElse(null);
		if (doc == null) {
			doc = f.documentos().stream()
					.filter(d -> d.descripcion() != null && EN_CASTELLANO.matcher(d.descripcion()).find())
					.findFirst()
					.orElse(f.documentos().isEmpty() ? null : f.documentos().getFirst());
		}
		return doc == null ? null : new Documento(doc.id(), doc.nombre());
	}

	private static List<Long> idsDocumentos(Ficha f) {
		return f.documentos().stream().map(DocumentoFicha::id).sorted().toList();
	}

	/** Compara campo a campo y apunta lo que cambia y lo que se conserva */
	private static final class Comparador {
		final List<String> campos = new ArrayList<>();
		final List<String> conservados = new ArrayList<>();

		<T> T elegir(String campo, T antes, T ahora) {
			if (igual(antes, ahora)) return antes;
			if (vacio(ahora) && !vacio(antes)) {
				conservados.add(campo);
				return antes;
			}
			campos.add(campo);
			return ahora;
		}

		private static boolean igual(Object a, Object b) {
			if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
			return Objects.equals(a, b);
		}

		private static boolean vacio(Object v) {
			return v == null || (v instanceof Collection<?> c && c.isEmpty());
		}
	}
}
