package es.felixcorcoran.subvenciones.encaje.aplicacion;

import java.time.LocalDate;
import java.util.List;

import es.felixcorcoran.subvenciones.encaje.dominio.Convocatoria;

/** Puerto: las convocatorias que conoce este servicio (su propia copia de lectura) */
public interface Convocatorias {

	/** La guarda o sustituye la versión guardada */
	void guardar(Convocatoria convocatoria);

	/** Las que no han cerrado ese día, incluidas las que no tienen fecha de cierre conocida */
	List<Convocatoria> abiertasEl(LocalDate dia);
}
