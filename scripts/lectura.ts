/**
 * Qué convocatorias tiene que leer la IA (scripts/analizar.ts) y si lo que
 * devuelve se puede guardar.
 */
import type { Analisis, Convocatoria } from '../src/lib/tipos.ts';
import { mencionaFecha, plazoPrevio } from '../src/lib/fechas.ts';
import { estado } from '../src/lib/estado.ts';

/** Intentos fallidos seguidos antes de esperar */
export const MAX_INTENTOS = 3;
/** Tras agotar los intentos, días hasta volver a probar: nada se queda bloqueado para siempre */
export const DIAS_ESPERA = 14;

export function porLeer(c: Convocatoria, hoy: string): boolean {
	if (!c.documento) return false;
	if (c.ia && !c.iaRevisar) return false;
	// Una «por verificar» sin leer se queda así: leerla ahora la haría nueva para
	// el repo de alertas, que avisaría de ella como si siguiera abierta
	if (!c.ia && estado(c, hoy) === 'por_verificar') return false;
	if ((c.iaIntentos ?? 0) < MAX_INTENTOS) return true;
	// Agotados los intentos: otra vez pasada la espera (sin fecha: datos de antes, se prueba ya)
	if (!c.iaIntentadoEl) return true;
	const desde = new Date(`${hoy}T00:00:00Z`);
	desde.setUTCDate(desde.getUTCDate() - DIAS_ESPERA);
	return c.iaIntentadoEl <= desde.toISOString().slice(0, 10);
}

export { mencionaFecha };

/**
 * Por qué no vale la fecha de cierre que sacó la IA de las bases, o null si
 * vale. Solo se acepta si no contradice a la BDNS y es coherente con lo que la
 * propia lectura dice del plazo: una fecha dudosa no cierra una convocatoria.
 * La excepción es un plazo previo al periodo de la BDNS (plazoPrevio, fechas.ts).
 */
export function motivoCierreIaDudoso(c: Convocatoria): string | null {
	const fecha = c.ia?.plazoFin;
	if (!c.ia || !fecha) return null;
	if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) return 'no es una fecha';
	// Única excepción a que mande la BDNS: un plazo para el negocio anterior al
	// periodo de la BDNS, con las tres condiciones de plazoPrevio (fechas.ts)
	if (plazoPrevio(c)) return null;
	// Con fecha en la BDNS, manda la BDNS: si coincide, está confirmada (aunque las bases tengan dudas)
	if (c.fin) return c.fin === fecha ? null : `no coincide con la de la BDNS (${c.fin})`;
	if (fecha < c.publicada) return 'es anterior al registro en la BDNS';
	if (c.inicio && fecha < c.inicio) return `es anterior a la apertura del plazo en la BDNS (${c.inicio})`;
	if (!mencionaFecha(c.ia.plazo ?? '', fecha)) return 'el plazo leído en las bases no nombra esa fecha';
	if (c.ia.dudas && /plazo|fecha|cierre|solicitud/i.test(c.ia.dudas)) return 'la lectura de las bases tiene dudas sobre el plazo';
	return null;
}

/**
 * Deja en `ia.plazoFin` solo una fecha de las bases que se pueda usar; la
 * descartada se guarda en `cierreDescartado` con el motivo. Así cualquiera que
 * lea los datos (la web y el repo de alertas, con «ia.plazoFin, si no fin»)
 * usa la fecha buena sin repetir esta regla. No llama a la IA.
 */
export function revisarCierreIa(c: Convocatoria): boolean {
	const motivo = motivoCierreIaDudoso(c);
	if (!motivo) return false;
	c.cierreDescartado = { fecha: c.ia!.plazoFin!, motivo };
	c.ia = { ...c.ia!, plazoFin: null };
	return true;
}

const SINO = new Set(['si', 'no', 'no_consta']);
const texto = (v: unknown) => typeof v === 'string';
const numeroONulo = (v: unknown) => v === null || (typeof v === 'number' && Number.isFinite(v));
const textos = (v: unknown) => Array.isArray(v) && v.every(texto);

/** El esquema ya se pide a Gemini, pero se comprueba antes de guardar: no se fía de la respuesta */
export function analisisValido(a: unknown): a is Analisis {
	if (!a || typeof a !== 'object') return false;
	const x = a as Record<string, unknown>;
	const k = x.condiciones as Record<string, unknown> | undefined;
	return (
		['ayuda', 'premio', 'otro'].includes(x.categoria as string) &&
		texto(x.resumen) &&
		(x.resumen as string).trim() !== '' &&
		texto(x.paraQuien) &&
		texto(x.importe) &&
		numeroONulo(x.importeMaximo) &&
		texto(x.plazo) &&
		(x.plazoFin === null || texto(x.plazoFin)) &&
		textos(x.requisitos) &&
		textos(x.gastos) &&
		(x.dudas === null || texto(x.dudas)) &&
		!!k &&
		typeof k === 'object' &&
		SINO.has(k.autonomos as string) &&
		SINO.has(k.sociedades as string) &&
		SINO.has(k.emprendedores as string) &&
		numeroONulo(k.maxEmpleados) &&
		numeroONulo(k.antiguedadMinMeses) &&
		numeroONulo(k.antiguedadMaxMeses) &&
		(k.municipio === null || texto(k.municipio))
	);
}
