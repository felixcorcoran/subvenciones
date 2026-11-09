package es.felixcorcoran.subvenciones.recogida.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import es.felixcorcoran.subvenciones.recogida.dominio.Convocatoria;

/** Puerto: las convocatorias guardadas, las descartadas y las pasadas hechas */
public interface Almacen {

	Optional<Convocatoria> guardada(String codigo);

	List<Convocatoria> guardadas();

	void guardar(Convocatoria convocatoria);

	/** Fuera de las guardadas */
	void retirar(String codigo);

	/** Ya vista y descartada: no se vuelve a pedir su ficha mientras siga apuntada */
	boolean descartada(String codigo);

	void descartar(String codigo, String motivo, LocalDate fecha);

	/** Las descartadas solo hacen falta mientras puedan volver a salir en la búsqueda */
	void olvidarDescartadasAntesDe(LocalDate fecha);

	Optional<Instant> ultimaPasada();

	void apuntarPasada(Instant cuando, Resumen resumen);
}
