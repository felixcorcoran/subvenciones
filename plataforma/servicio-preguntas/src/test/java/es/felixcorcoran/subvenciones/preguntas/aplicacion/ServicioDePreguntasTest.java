package es.felixcorcoran.subvenciones.preguntas.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Documentos;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.IaNoDisponible;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Pasajes;
import es.felixcorcoran.subvenciones.preguntas.aplicacion.ServicioDePreguntas.SinBases;
import es.felixcorcoran.subvenciones.preguntas.dominio.Citas;
import es.felixcorcoran.subvenciones.preguntas.dominio.Documento;
import es.felixcorcoran.subvenciones.preguntas.dominio.Pasaje;
import es.felixcorcoran.subvenciones.preguntas.dominio.Respuesta;

/** «Pregunta a la convocatoria» con todo en memoria: búsqueda por coseno y una IA que cita el primer pasaje */
class ServicioDePreguntasTest {

	private static final List<String> BASES = List.of(
			"Base 1. Objeto.\nAyudas a la modernización del comercio minorista del municipio.",
			"Base 2. Beneficiarios.\nPodrán ser beneficiarias las personas trabajadoras autónomas y las pequeñas empresas con establecimiento abierto.",
			"Base 3. Cuantía.\nLa ayuda máxima será de tres mil euros por establecimiento comercial.",
			"Base 4. Plazo.\nEl plazo de presentación de solicitudes será de quince días hábiles.");

	private final IndiceEnMemoria indice = new IndiceEnMemoria();
	private final Set<UUID> procesados = new HashSet<>();
	private final List<Long> descargados = new ArrayList<>();
	private List<String> paginas = BASES;
	private boolean iaDisponible = true;

	/** La IA de prueba: cita literalmente la primera frase del pasaje más parecido */
	private final Puertos.Redactor redactor = (titulo, pregunta, contexto) -> {
		if (!iaDisponible) throw new IaNoDisponible("cupo agotado");
		String primera = contexto.getFirst().texto().split("\n")[1];
		return new Citas.Propuesta(true, "Según las bases: " + primera, List.of(new Citas.CitaPropuesta(1, primera)), "prueba");
	};

	private final ServicioDePreguntas servicio = new ServicioDePreguntas(indice, indice, id -> {
		descargados.add(id);
		return new byte[] { '%', 'P', 'D', 'F' };
	}, pdf -> paginas, new PalabrasComoVectores(), redactor, procesados::add, Runnable::run);

	private void llega(String codigo, Long documentoId, String nombre) {
		servicio.alRecibir(UUID.randomUUID(), codigo, "Ayudas al comercio " + codigo, documentoId, nombre);
	}

	@Test
	void respondeConLaCitaYLaPaginaDelPasajeQueTrataDeLaPregunta() {
		llega("1", 10L, "bases.pdf");
		servicio.indexarPendientes(10);

		Respuesta r = servicio.preguntar("1", "¿Cuál es la ayuda máxima por establecimiento?");

		assertThat(r.consta()).isTrue();
		assertThat(r.citas()).singleElement().satisfies(c -> {
			assertThat(c.pagina()).isEqualTo(3);
			assertThat(c.texto()).contains("tres mil euros");
		});
	}

	@Test
	void unaSinIndexarSeIndexaAlPreguntarleUnaSolaVez() {
		llega("1", 10L, "bases.pdf");

		servicio.preguntar("1", "¿Quién puede ser beneficiaria?");
		servicio.preguntar("1", "¿Cuánto es la ayuda?");

		assertThat(descargados).containsExactly(10L);
		assertThat(indice.documentos.get("1").estado()).isEqualTo(Documento.Estado.INDEXADO);
	}

	@Test
	void unPdfEscaneadoOQueNoEsPdfNoSePuedeConsultarYSeDiceElMotivo() {
		paginas = List.of("", "  ", "CSV: 1234");
		llega("escaneado", 10L, "bases.pdf");
		llega("word", 11L, "bases.docx");

		assertThatThrownBy(() -> servicio.preguntar("escaneado", "¿Plazo?")).isInstanceOf(SinBases.class)
				.hasMessageContaining("no tiene texto");
		assertThatThrownBy(() -> servicio.preguntar("word", "¿Plazo?")).isInstanceOf(SinBases.class)
				.hasMessageContaining("no están en PDF");
		assertThat(descargados).containsExactly(10L);
	}

	@Test
	void siCambiaElDocumentoSeVuelveAIndexar() {
		llega("1", 10L, "bases.pdf");
		servicio.indexarPendientes(10);
		llega("1", 10L, "bases.pdf");
		assertThat(indice.documentos.get("1").estado()).isEqualTo(Documento.Estado.INDEXADO);

		llega("1", 20L, "bases-corregidas.pdf");
		assertThat(indice.documentos.get("1").estado()).isEqualTo(Documento.Estado.PENDIENTE);
		servicio.indexarPendientes(10);
		assertThat(descargados).containsExactly(10L, 20L);
	}

	@Test
	void elMismoEventoDosVecesSoloCuentaUna() {
		UUID id = UUID.randomUUID();
		assertThat(servicio.alRecibir(id, "1", "x", 10L, "bases.pdf")).isTrue();
		assertThat(servicio.alRecibir(id, "1", "x", 20L, "otras.pdf")).isFalse();
	}

	@Test
	void siLaIaNoEstaDisponibleSeDiceAsiYNoSeInventaNada() {
		llega("1", 10L, "bases.pdf");
		iaDisponible = false;
		assertThatThrownBy(() -> servicio.preguntar("1", "¿Plazo de presentación?")).isInstanceOf(IaNoDisponible.class);
	}

	@Test
	void unaPreguntaVaciaOEnormeNoSeAcepta() {
		llega("1", 10L, "bases.pdf");
		assertThatThrownBy(() -> servicio.preguntar("1", " ? ")).isInstanceOf(ServicioDePreguntas.PreguntaNoValida.class);
		assertThatThrownBy(() -> servicio.preguntar("1", "x".repeat(501))).isInstanceOf(ServicioDePreguntas.PreguntaNoValida.class);
		assertThatThrownBy(() -> servicio.preguntar("no-existe", "¿Plazo?")).isInstanceOf(ServicioDePreguntas.NoEncontrada.class);
	}
}
