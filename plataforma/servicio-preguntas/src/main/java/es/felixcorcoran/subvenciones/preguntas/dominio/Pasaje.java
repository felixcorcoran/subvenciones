package es.felixcorcoran.subvenciones.preguntas.dominio;

/**
 * Un trozo de las bases de una convocatoria, con la página de la que sale: es lo que se busca y lo
 * que se cita.
 *
 * @param orden posición en el documento, empezando en 0
 * @param pagina página del PDF, empezando en 1
 */
public record Pasaje(String codigo, int orden, int pagina, String texto) {
}
