package es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.LectorPdf;

/** El texto de cada página con PDFBox; un PDF escaneado da páginas vacías */
public class LectorPdfBox implements LectorPdf {

	@Override
	public List<String> paginas(byte[] pdf) {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			var texto = new PDFTextStripper();
			texto.setSortByPosition(true);
			List<String> paginas = new ArrayList<>();
			for (int p = 1; p <= doc.getNumberOfPages(); p++) {
				texto.setStartPage(p);
				texto.setEndPage(p);
				paginas.add(texto.getText(doc));
			}
			return paginas;
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer el PDF", e);
		}
	}
}
