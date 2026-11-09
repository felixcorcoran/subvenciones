package es.felixcorcoran.subvenciones.recogida.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Codigo;
import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria.Organo;
import es.felixcorcoran.subvenciones.recogida.dominio.Ficha.DocumentoFicha;

/** Fichas de prueba: una convocatoria normal y abierta, para cambiarle lo que haga falta */
public final class Fichas {

	private Fichas() {
	}

	public static final DocumentoFicha EN_CATALAN = new DocumentoFicha(10, "Documento de la convocatoria en lengua cooficial", "conv-ca.pdf");
	public static final DocumentoFicha EN_CASTELLANO = new DocumentoFicha(20, "Documento de la convocatoria en español", "conv-es.pdf");

	public static Ficha ficha(String codigo) {
		return new Ficha(codigo, "Ayudas al comercio " + codigo, new Organo("LOCAL", "MÁLAGA", "AYUNTAMIENTO DE MÁLAGA"),
				null, LocalDate.of(2026, 11, 2), "Concurrencia competitiva - canónica", new BigDecimal("600000"),
				List.of("PYME Y PERSONAS FÍSICAS QUE DESARROLLAN ACTIVIDAD ECONÓMICA"),
				List.of(new Codigo("G", "COMERCIO AL POR MAYOR Y AL POR MENOR")), List.of(new Codigo("ES617", "Málaga")),
				"Comercio, Turismo y Pymes", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 12, 15), null, null,
				"https://bop.example/bases", List.of(EN_CATALAN, EN_CASTELLANO));
	}

	public static Ficha conFin(Ficha f, LocalDate fin) {
		return new Ficha(f.codigo(), f.titulo(), f.organo(), f.sede(), f.publicada(), f.tipo(), f.presupuesto(),
				f.beneficiarios(), f.sectores(), f.regiones(), f.finalidad(), f.inicio(), fin, f.textoInicio(), f.textoFin(),
				f.bases(), f.documentos());
	}

	public static Ficha conTipo(Ficha f, String tipo) {
		return new Ficha(f.codigo(), f.titulo(), f.organo(), f.sede(), f.publicada(), tipo, f.presupuesto(),
				f.beneficiarios(), f.sectores(), f.regiones(), f.finalidad(), f.inicio(), f.fin(), f.textoInicio(),
				f.textoFin(), f.bases(), f.documentos());
	}

	public static Ficha conDocumentos(Ficha f, List<DocumentoFicha> documentos) {
		return new Ficha(f.codigo(), f.titulo(), f.organo(), f.sede(), f.publicada(), f.tipo(), f.presupuesto(),
				f.beneficiarios(), f.sectores(), f.regiones(), f.finalidad(), f.inicio(), f.fin(), f.textoInicio(),
				f.textoFin(), f.bases(), documentos);
	}

	public static Ficha sinBasesNiSectores(Ficha f) {
		return new Ficha(f.codigo(), f.titulo(), f.organo(), f.sede(), f.publicada(), f.tipo(), f.presupuesto(),
				f.beneficiarios(), List.of(), f.regiones(), f.finalidad(), f.inicio(), f.fin(), f.textoInicio(),
				f.textoFin(), null, f.documentos());
	}
}
