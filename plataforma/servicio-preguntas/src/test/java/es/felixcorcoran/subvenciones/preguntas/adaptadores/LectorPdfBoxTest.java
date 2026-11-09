package es.felixcorcoran.subvenciones.preguntas.adaptadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.pdf.LectorPdfBox;

class LectorPdfBoxTest {

	/** Un PDF de verdad, hecho aquí: una página con texto, una en blanco (como un escaneo) y otra con texto */
	private static byte[] pdf(String... paginas) throws Exception {
		try (var doc = new PDDocument(); var salida = new ByteArrayOutputStream()) {
			for (String texto : paginas) {
				var pagina = new PDPage();
				doc.addPage(pagina);
				if (texto.isEmpty()) continue;
				try (var contenido = new PDPageContentStream(doc, pagina)) {
					contenido.beginText();
					contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
					contenido.newLineAtOffset(72, 700);
					contenido.showText(texto);
					contenido.endText();
				}
			}
			doc.save(salida);
			return salida.toByteArray();
		}
	}

	@Test
	void devuelveElTextoDeCadaPaginaEnOrdenYVaciasLasQueNoTienen() throws Exception {
		List<String> paginas = new LectorPdfBox().paginas(pdf("Base primera. Objeto de las ayudas.", "", "Base tercera. Plazo de diez dias."));

		assertThat(paginas).hasSize(3);
		assertThat(paginas.get(0)).contains("Objeto de las ayudas");
		assertThat(paginas.get(1)).isBlank();
		assertThat(paginas.get(2)).contains("Plazo de diez dias");
	}

	@Test
	void loQueNoEsUnPdfEsUnError() {
		assertThatThrownBy(() -> new LectorPdfBox().paginas("<html>error</html>".getBytes())).isInstanceOf(RuntimeException.class);
	}
}
