package es.felixcorcoran.subvenciones.avisos.adaptadores.salida.persistencia;

import org.springframework.transaction.support.TransactionTemplate;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Transacciones;

/** Las transacciones de la base de datos, para que la aplicación no dependa de Spring */
public class TransaccionesSpring implements Transacciones {

	private final TransactionTemplate plantilla;

	public TransaccionesSpring(TransactionTemplate plantilla) {
		this.plantilla = plantilla;
	}

	@Override
	public void enUna(Runnable trabajo) {
		plantilla.executeWithoutResult(estado -> trabajo.run());
	}
}
