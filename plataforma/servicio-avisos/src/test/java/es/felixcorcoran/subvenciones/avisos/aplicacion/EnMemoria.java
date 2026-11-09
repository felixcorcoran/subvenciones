package es.felixcorcoran.subvenciones.avisos.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Avisos;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Correo;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.EventosProcesados;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Novedades;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Pasadas;
import es.felixcorcoran.subvenciones.avisos.aplicacion.Puertos.Suscriptores;
import es.felixcorcoran.subvenciones.avisos.dominio.Encaje;
import es.felixcorcoran.subvenciones.avisos.dominio.Novedad;
import es.felixcorcoran.subvenciones.avisos.dominio.Suscriptor;

/** Todos los puertos de almacenamiento en memoria, y un buzón que guarda los correos */
class EnMemoria implements Suscriptores, Novedades, Avisos, Pasadas, EventosProcesados, Correo {

	final Map<UUID, Suscriptor> suscriptores = new LinkedHashMap<>();
	final Map<String, Novedad> novedades = new LinkedHashMap<>();
	final Set<String> repartidas = new HashSet<>();
	/** suscriptor → código → enviado (null si pendiente) */
	final Map<UUID, Map<String, Instant>> avisos = new LinkedHashMap<>();
	final List<Instant> pasadas = new ArrayList<>();
	final Set<UUID> procesados = new HashSet<>();
	final List<Mensaje> buzon = new ArrayList<>();

	@Override
	public Optional<Suscriptor> porEmail(String email) {
		return suscriptores.values().stream().filter(s -> s.email().equals(email)).findFirst();
	}

	@Override
	public Optional<Suscriptor> porToken(String token) {
		return suscriptores.values().stream().filter(s -> s.token().equals(token)).findFirst();
	}

	@Override
	public Optional<Suscriptor> porId(UUID id) {
		return Optional.ofNullable(suscriptores.get(id));
	}

	@Override
	public List<Suscriptor> activos() {
		return suscriptores.values().stream().filter(Suscriptor::activo).toList();
	}

	@Override
	public void guardar(Suscriptor s) {
		suscriptores.put(s.id(), s);
	}

	@Override
	public void borrar(UUID id) {
		suscriptores.remove(id);
		avisos.remove(id);
	}

	@Override
	public long borrarPendientesCreadosAntesDe(Instant limite) {
		var viejos = suscriptores.values().stream().filter(s -> !s.activo() && s.creado().isBefore(limite)).map(Suscriptor::id).toList();
		viejos.forEach(this::borrar);
		return viejos.size();
	}

	@Override
	public void guardar(Novedad n) {
		novedades.put(n.codigo(), n);
	}

	@Override
	public List<Novedad> sinRepartir() {
		return novedades.values().stream().filter(n -> !repartidas.contains(n.codigo())).toList();
	}

	@Override
	public void marcarRepartidas(List<String> codigos) {
		repartidas.addAll(codigos);
	}

	@Override
	public void crear(UUID suscriptor, String codigo, Encaje.Estado estado, Instant ahora) {
		avisos.computeIfAbsent(suscriptor, k -> new LinkedHashMap<>()).putIfAbsent(codigo, null);
	}

	@Override
	public Map<UUID, List<String>> pendientes() {
		Map<UUID, List<String>> r = new LinkedHashMap<>();
		avisos.forEach((s, suyos) -> suyos.forEach((codigo, enviado) -> {
			if (enviado == null) r.computeIfAbsent(s, k -> new ArrayList<>()).add(codigo);
		}));
		return r;
	}

	@Override
	public void marcarEnviados(UUID suscriptor, Instant ahora) {
		avisos.getOrDefault(suscriptor, Map.of()).replaceAll((codigo, enviado) -> enviado == null ? ahora : enviado);
	}

	@Override
	public long correosDesde(Instant desde) {
		return avisos.values().stream().filter(m -> m.values().stream().anyMatch(e -> e != null && !e.isBefore(desde))).count();
	}

	@Override
	public boolean hayAlguna() {
		return !pasadas.isEmpty();
	}

	@Override
	public void apuntar(Instant cuando, ServicioDeAvisos.Resumen resumen) {
		pasadas.add(cuando);
	}

	@Override
	public boolean apuntar(UUID id) {
		return procesados.add(id);
	}

	@Override
	public void enviar(Mensaje mensaje) {
		buzon.add(mensaje);
	}
}
