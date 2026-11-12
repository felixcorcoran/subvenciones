/**
 * Lo que la web recuerda en el navegador, sin cuentas ni servidor: las
 * ayudas guardadas y el día de la visita anterior (para marcar las nuevas).
 * Todo con try/catch: sin almacenamiento (modo privado, bloqueado) la web
 * funciona igual, solo que sin recordar.
 */
import type { ConvocatoriaWeb } from './tipos.ts';

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

export function guardadas(a = almacen()): Set<string> {
	try {
		const v = JSON.parse(a?.getItem(CLAVE_GUARDADAS) ?? '[]') as unknown;
		return new Set(Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);
	} catch {
		return new Set();
	}
}

/** Guarda o quita una ayuda; devuelve si queda guardada */
export function alternarGuardada(codigo: string, a = almacen()): boolean {
	const s = guardadas(a);
	const ahora = !s.has(codigo);
	if (ahora) s.add(codigo);
	else s.delete(codigo);
	try {
		a?.setItem(CLAVE_GUARDADAS, JSON.stringify([...s]));
	} catch {}
	return ahora;
}

/**
 * Apunta la visita de hoy y devuelve el día de la anterior (otro día), o null
 * si es la primera. Volver el mismo día no cuenta como otra visita: las
 * nuevas siguen marcadas hasta mañana.
 */
export function registrarVisita(hoy: string, a = almacen()): string | null {
	try {
		const v = JSON.parse(a?.getItem(CLAVE_VISITA) ?? 'null') as { dia?: string; anterior?: string | null } | null;
		if (v?.dia === hoy) return v.anterior ?? null;
		const anterior = v?.dia ?? null;
		a?.setItem(CLAVE_VISITA, JSON.stringify({ dia: hoy, anterior }));
		return anterior;
	} catch {
		return null;
	}
}

/** Si la ayuda entró en el radar después de la visita anterior */
export const esNueva = (c: Pick<ConvocatoriaWeb, 'alta' | 'publicada'>, anterior: string | null) => anterior !== null && (c.alta ?? c.publicada) > anterior;
