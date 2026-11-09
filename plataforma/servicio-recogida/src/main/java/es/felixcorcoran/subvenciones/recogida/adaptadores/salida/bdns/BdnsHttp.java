package es.felixcorcoran.subvenciones.recogida.adaptadores.salida.bdns;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import es.felixcorcoran.subvenciones.recogida.aplicacion.Bdns;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Codigo;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Organo;
import es.felixcorcoran.subvenciones.recogida.dominio.Ficha;
import es.felixcorcoran.subvenciones.recogida.dominio.Ficha.DocumentoFicha;

/**
 * La API pública de la BDNS (https://www.infosubvenciones.es/bdnstrans/api). Sin clave. Su aviso
 * legal permite reutilizar los datos y avisa de que puede limitar el acceso si se abusa: por eso
 * peticiones de una en una, con pausa entre ellas, y tres intentos como mucho cuando el fallo es
 * suyo (5xx o sin respuesta).
 */
public class BdnsHttp implements Bdns {

	/** Ritmo de las peticiones: la pausa entre una y otra, y la espera antes de reintentar (por intento) */
	public record Ritmo(Duration pausa, Duration esperaReintento) {
	}

	private static final DateTimeFormatter FECHA_BUSQUEDA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Pattern FECHA_ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}");
	private static final int INTENTOS = 3;

	private final RestClient http;
	private final Ritmo ritmo;
	private long ultima;

	public BdnsHttp(RestClient http, Ritmo ritmo) {
		this.http = http;
		this.ritmo = ritmo;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Resultado(String numeroConvocatoria) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Pagina(List<Resultado> content, boolean last) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record FichaBdns(String codigoBDNS, OrganoBdns organo, String sedeElectronica, String fechaRecepcion,
			String tipoConvocatoria, BigDecimal presupuestoTotal, String descripcion,
			List<Descripcion> tiposBeneficiarios, List<SectorBdns> sectores, List<Descripcion> regiones,
			String descripcionFinalidad, String urlBasesReguladoras, String fechaInicioSolicitud,
			String fechaFinSolicitud, String textInicio, String textFin, List<DocumentoBdns> documentos) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OrganoBdns(String nivel1, String nivel2, String nivel3) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Descripcion(String descripcion) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SectorBdns(String descripcion, String codigo) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record DocumentoBdns(long id, String descripcion, String nombreFic) {
	}

	/** Tipo de beneficiario 3 (pyme y personas físicas con actividad económica, autónomos incluidos) y 4 (gran empresa) */
	@Override
	public List<String> buscar(LocalDate desde, LocalDate hasta) {
		List<String> codigos = new ArrayList<>();
		for (int pagina = 0;; pagina++) {
			int p = pagina;
			Pagina datos = pedir(() -> http.get()
					.uri(u -> u.path("/convocatorias/busqueda")
							.queryParam("page", p)
							.queryParam("pageSize", 100)
							.queryParam("fechaDesde", FECHA_BUSQUEDA.format(desde))
							.queryParam("fechaHasta", FECHA_BUSQUEDA.format(hasta))
							.queryParam("tiposBeneficiario", "3,4")
							.build())
					.retrieve()
					.body(Pagina.class));
			if (datos == null || datos.content() == null || datos.content().isEmpty()) break;
			datos.content().forEach(r -> codigos.add(r.numeroConvocatoria()));
			if (datos.last()) break;
		}
		return codigos;
	}

	@Override
	public ResultadoFicha ficha(String codigo) {
		FichaBdns f;
		try {
			f = pedir(() -> http.get()
					.uri(u -> u.path("/convocatorias").queryParam("numConv", codigo).build())
					.retrieve()
					.body(FichaBdns.class));
		} catch (RestClientException e) {
			String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			return new ResultadoFicha.Fallo(motivo.substring(0, Math.min(200, motivo.length())));
		}
		if (!valida(f, codigo)) return new ResultadoFicha.Fallo("ficha incompleta");
		return new ResultadoFicha.Leida(aFicha(f));
	}

	/** Una ficha con lo mínimo para fiarse de ella; si no, se trata como un fallo de consulta */
	static boolean valida(FichaBdns f, String codigo) {
		return f != null
				&& codigo.equals(f.codigoBDNS())
				&& f.descripcion() != null && !f.descripcion().isBlank()
				&& f.fechaRecepcion() != null && FECHA_ISO.matcher(f.fechaRecepcion()).find()
				&& f.organo() != null
				&& f.documentos() != null
				&& f.tiposBeneficiarios() != null
				&& f.sectores() != null
				&& f.regiones() != null;
	}

	static Ficha aFicha(FichaBdns f) {
		OrganoBdns o = f.organo();
		return new Ficha(
				f.codigoBDNS(),
				f.descripcion().strip(),
				new Organo(o.nivel1(), o.nivel2(), o.nivel3()),
				limpio(f.sedeElectronica()),
				fecha(f.fechaRecepcion()),
				limpio(f.tipoConvocatoria()),
				f.presupuestoTotal(),
				f.tiposBeneficiarios().stream().map(b -> b.descripcion().strip()).toList(),
				f.sectores().stream().map(s -> new Codigo(s.codigo().strip(), s.descripcion().strip())).toList(),
				f.regiones().stream().map(r -> region(r.descripcion())).toList(),
				limpio(f.descripcionFinalidad()),
				fecha(f.fechaInicioSolicitud()),
				fecha(f.fechaFinSolicitud()),
				limpio(f.textInicio()),
				limpio(f.textFin()),
				limpio(f.urlBasesReguladoras()),
				f.documentos().stream().map(d -> new DocumentoFicha(d.id(), d.descripcion(), d.nombreFic())).toList());
	}

	/** «ES617 - Málaga» → (ES617, Málaga) */
	private static Codigo region(String descripcion) {
		String[] partes = descripcion.split(" - ", 2);
		return new Codigo(partes[0].strip(), partes.length > 1 ? partes[1].strip() : "");
	}

	private static String limpio(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	private static LocalDate fecha(String s) {
		return s != null && FECHA_ISO.matcher(s).find() ? LocalDate.parse(s.substring(0, 10)) : null;
	}

	/** Con pausa entre peticiones y reintentos solo si el fallo es de ellos */
	private synchronized <T> T pedir(Supplier<T> peticion) {
		for (int intento = 1;; intento++) {
			esperar(ultima + ritmo.pausa().toMillis() - System.currentTimeMillis());
			ultima = System.currentTimeMillis();
			try {
				return peticion.get();
			} catch (HttpServerErrorException | ResourceAccessException e) {
				if (intento == INTENTOS) throw e;
			}
			esperar(ritmo.esperaReintento().toMillis() * intento);
		}
	}

	private static void esperar(long ms) {
		if (ms <= 0) return;
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrumpido esperando a la BDNS", e);
		}
	}
}
