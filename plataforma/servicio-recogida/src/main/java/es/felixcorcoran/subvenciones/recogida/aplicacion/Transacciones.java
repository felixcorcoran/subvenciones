package es.felixcorcoran.subvenciones.recogida.aplicacion;

/** Puerto: hacer varias cosas como una sola (todo o nada) */
public interface Transacciones {

	void enUna(Runnable trabajo);
}
