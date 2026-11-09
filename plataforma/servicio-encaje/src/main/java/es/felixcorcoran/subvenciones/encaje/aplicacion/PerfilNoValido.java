package es.felixcorcoran.subvenciones.encaje.aplicacion;

/** Una respuesta del perfil que no es de las del formulario */
public class PerfilNoValido extends RuntimeException {

	private final String dato;

	public PerfilNoValido(String dato) {
		super("El perfil no es válido: " + dato);
		this.dato = dato;
	}

	public String dato() {
		return dato;
	}
}
