package es.felixcorcoran.subvenciones.encaje.dominio;

/** Puerto: provincias (NUTS) y secciones de la CNAE-2025, los mismos catálogos que usa la web */
public interface Catalogo {

	boolean esProvincia(String codigoNuts);

	/** El nombre de una zona bien escrito, o el original si no se conoce (la BDNS pone «PAIS VASCO») */
	String nombreZona(String codigoNuts, String original);

	/** La sección (letra) de un código de sector de la BDNS (una letra o una división), o null */
	String seccionDe(String codigoSector);

	String nombreSeccion(String letra);
}
