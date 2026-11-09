package es.felixcorcoran.subvenciones.preguntas.adaptadores.salida.bdns;

import java.time.Duration;

import org.springframework.web.client.RestClient;

import es.felixcorcoran.subvenciones.preguntas.aplicacion.Puertos.Descargas;

/** Los documentos de las bases, de la API pública de la BDNS, de uno en uno y con pausa */
public class DescargasBdns implements Descargas {

	private final RestClient http;
	private final Duration pausa;
	private long ultima;

	public DescargasBdns(RestClient http, Duration pausa) {
		this.http = http;
		this.pausa = pausa;
	}

	@Override
	public synchronized byte[] descargar(long id) {
		long espera = ultima + pausa.toMillis() - System.currentTimeMillis();
		if (espera > 0) {
			try {
				Thread.sleep(espera);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrumpido", e);
			}
		}
		ultima = System.currentTimeMillis();
		byte[] bytes = http.get()
				.uri(u -> u.path("/convocatorias/documentos").queryParam("idDocumento", id).build())
				.retrieve()
				.body(byte[].class);
		if (bytes == null || bytes.length == 0) throw new IllegalStateException("documento vacío");
		return bytes;
	}
}
