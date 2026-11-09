package es.felixcorcoran.subvenciones.preguntas.evaluacion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.pdf.LectorPdfBox;
import tools.jackson.databind.json.JsonMapper;

/**
 * Herramienta para preparar la evaluación: saca el texto de cada página de unos PDF con el mismo
 * lector que usa el servicio y lo guarda como JSON (evaluacion/documentos/{codigo}.json). Así el
 * repositorio no guarda los PDF, que pueden ocupar megas.
 *
 * <pre>
 * java -cp … ExtraerPaginas carpeta-con-los-pdf src/test/resources/evaluacion/documentos
 * </pre>
 */
public final class ExtraerPaginas {

	private ExtraerPaginas() {
	}

	public static void main(String[] args) throws Exception {
		Path origen = Path.of(args[0]);
		Path destino = Path.of(args[1]);
		JsonMapper json = JsonMapper.builder().build();
		try (var pdfs = Files.list(origen)) {
			for (Path pdf : pdfs.filter(p -> p.toString().endsWith(".pdf")).sorted().toList()) {
				List<String> paginas = new LectorPdfBox().paginas(Files.readAllBytes(pdf));
				String codigo = pdf.getFileName().toString().replace(".pdf", "");
				Files.writeString(destino.resolve(codigo + ".json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(paginas));
				System.out.println(codigo + ": " + paginas.size() + " páginas, "
						+ paginas.stream().mapToInt(String::length).sum() + " caracteres");
			}
		}
	}
}
