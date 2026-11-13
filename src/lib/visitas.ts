/**
 * Lo que la web recuerda en el navegador, sin cuentas ni servidor: las
 * ayudas guardadas (con el plazo que tenían, para avisar si cambia o si
 * desaparecen), el día de la visita anterior (para marcar las nuevas) y los
 * negocios guardados (una gestoría puede tener uno por cliente).
 * Todo con try/catch: sin almacenamiento (modo privado, bloqueado) la web
 * funciona igual, solo que sin recordar.
 */
import type { Perfil } from './encaje.ts';
import type { ConvocatoriaWeb } from './tipos.ts';
import { cierre } from './fechas.ts';

type Almacen = Pick<Storage, 'getItem' | 'setItem'>;
const almacen = (): Almacen | null => {
	try {
		return globalThis.localStorage ?? null;
	} catch {
		return null;
	}
};

const CLAVE_GUARDADAS = 'ayudas-guardadas';
const CLAVE_VISITA = 'ultima-visita';
const CLAVE_NEGOCIOS = 'negocios-subvenciones';

const leer = <T>(a: Almacen | null, clave: string, porDefecto: T): unknown => {
	try {
		return JSON.parse(a?.getItem(clave) ?? 'null') ?? porDefecto;
	} catch {
		return porDefecto;
	}
};
const escribir = (a: Almacen | null, clave: string, valor: unknown) => {
	try {
		a?.setItem(clave, JSON.stringify(valor));
	} catch {}
};

/** Código → fecha de cierre que tenía al guardarla (o al dar por vistos sus cambios); null si no tenía */
export type Guardadas = Record<string, string | null>;

/** Las guardadas; acepta el formato antiguo (una lista de códigos) */
export function guardadasConPlazo(a = almacen()): Guardadas {
	const v = leer(a, CLAVE_GUARDADAS, {});
	if (Array.isArray(v)) return Object.fromEntries(v.filter((x): x is string => typeof x === 'string').map((k) => [k, null]));
	if (!v || typeof v !== 'object') return {};
	return Object.fromEntries(Object.entries(v as Record<string, unknown>).map(([k, x]) => [k, typeof x === 'string' ? x : null]));
}

export const guardadas = (a = almacen()) => new Set(Object.keys(guardadasConPlazo(a)));

/** Guarda o quita una ayuda (con su plazo de ahora); devuelve si queda guardada */
export function alternarGuardada(c: Pick<ConvocatoriaWeb, 'codigo'> & Partial<ConvocatoriaWeb>, a = almacen()): boolean {
	const g = guardadasConPlazo(a);
	const ahora = !(c.codigo in g);
	if (ahora) g[c.codigo] = cierre(c as ConvocatoriaWeb) ?? null;
	else delete g[c.codigo];
	escribir(a, CLAVE_GUARDADAS, g);
	return ahora;
}

export interface CambioGuardada {
	codigo: string;
	/** plazo: la fecha de cierre ha cambiado; fuera: ya no está en la lista (lo normal, que haya cerrado) */
	tipo: 'plazo' | 'fuera';
	antes: string | null;
	ahora: string | null;
	c?: ConvocatoriaWeb;
}

/** Lo que ha cambiado en las guardadas desde que se guardaron (o desde que se dieron por vistos los cambios) */
export function cambiosGuardadas(lista: ConvocatoriaWeb[], a = almacen()): CambioGuardada[] {
	const g = guardadasConPlazo(a);
	const porCodigo = new Map(lista.map((c) => [c.codigo, c]));
	const cambios: CambioGuardada[] = [];
	for (const [codigo, antes] of Object.entries(g)) {
		const c = porCodigo.get(codigo);
		if (!c) cambios.push({ codigo, tipo: 'fuera', antes, ahora: null });
		else if ((cierre(c) ?? null) !== antes) cambios.push({ codigo, tipo: 'plazo', antes, ahora: cierre(c) ?? null, c });
	}
	return cambios;
}

/** Da por vistos los cambios: apunta el plazo de ahora y quita las que ya no están */
export function verCambios(lista: ConvocatoriaWeb[], a = almacen()) {
	const g = guardadasConPlazo(a);
	const porCodigo = new Map(lista.map((c) => [c.codigo, c]));
	const nuevas: Guardadas = {};
	for (const codigo of Object.keys(g)) {
		const c = porCodigo.get(codigo);
		if (c) nuevas[codigo] = cierre(c) ?? null;
	}
	escribir(a, CLAVE_GUARDADAS, nuevas);
}

/**
 * Apunta la visita de hoy y devuelve el día de la anterior (otro día), o null
 * si es la primera. Volver el mismo día no cuenta como otra visita: las
 * nuevas siguen marcadas hasta mañana.
 */
export function registrarVisita(hoy: string, a = almacen()): string | null {
	const v = leer(a, CLAVE_VISITA, null) as { dia?: string; anterior?: string | null } | null;
	if (v?.dia === hoy) return v.anterior ?? null;
	const anterior = v?.dia ?? null;
	escribir(a, CLAVE_VISITA, { dia: hoy, anterior });
	return anterior;
}

/** Si la ayuda entró en el radar después de la visita anterior */
export const esNueva = (c: Pick<ConvocatoriaWeb, 'alta' | 'publicada'>, anterior: string | null) => anterior !== null && (c.alta ?? c.publicada) > anterior;

/** Un negocio guardado: su nombre (lo pone quien lo guarda), sus respuestas y para qué busca la ayuda */
export interface Negocio {
	nombre: string;
	perfil: Perfil;
	finalidad?: string | null;
}

export const MAX_NEGOCIOS = 30;

export function negocios(a = almacen()): Negocio[] {
	const v = leer(a, CLAVE_NEGOCIOS, []);
	return Array.isArray(v)
		? v.filter((n): n is Negocio => Boolean(n && typeof n.nombre === 'string' && n.perfil && typeof n.perfil.provincia === 'string' && typeof n.perfil.forma === 'string'))
		: [];
}

/** Guarda un negocio (si ya hay uno con ese nombre, lo sustituye); el último, el primero */
export function guardarNegocio(n: Negocio, a = almacen()): Negocio[] {
	const nombre = n.nombre.trim().slice(0, 60);
	if (!nombre) return negocios(a);
	const lista = [{ ...n, nombre }, ...negocios(a).filter((x) => x.nombre.toLowerCase() !== nombre.toLowerCase())].slice(0, MAX_NEGOCIOS);
	escribir(a, CLAVE_NEGOCIOS, lista);
	return lista;
}

export function borrarNegocio(nombre: string, a = almacen()): Negocio[] {
	const lista = negocios(a).filter((x) => x.nombre !== nombre);
	escribir(a, CLAVE_NEGOCIOS, lista);
	return lista;
}
