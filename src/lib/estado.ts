/**
 * Cómo se cuenta el estado de una convocatoria en la web: sin fecha de cierre
 * confirmada, sin comprobar en la BDNS desde hace días o con cambios en su
 * ficha. Aparte de encaje.ts, que tiene un contrato con el repo de alertas.
 */
import type { ConvocatoriaWeb } from './tipos.ts';
import { cierre, fechaLarga, plazoPrevio } from './fechas.ts';

/** Días sin poder leer su ficha en la BDNS a partir de los cuales se avisa */
export const DIAS_SIN_COMPROBAR = 3;
/** Días desde el registro a partir de los cuales una sin fecha de cierre pasa a «por verificar» */
export const DIAS_POR_VERIFICAR = 120;

const restarDias = (hoy: string, dias: number) => {
	const d = new Date(`${hoy}T00:00:00Z`);
	d.setUTCDate(d.getUTCDate() - dias);
	return d.toISOString().slice(0, 10);
};

/**
 * - abierta: tiene fecha de cierre (de la BDNS o de las bases, ya revisada) y no ha pasado.
 * - proxima: el plazo aún no ha empezado.
 * - sin_fecha: no hay fecha de cierre que se pueda confirmar.
 * - por_verificar: sin fecha de cierre y registrada hace más de cuatro meses; puede
 *   que ya no esté abierta, pero no hay pruebas de que haya cerrado. Sale de la
 *   lista principal, no de los datos.
 * - cerrada: su fecha de cierre ha pasado.
 * - plazo_previo_cerrado: con plazo previo (plazoPrevio, fechas.ts), el plazo de
 *   las bases para el negocio ha pasado, pero el periodo de la BDNS sigue en
 *   curso o no tiene fin: no se dice cerrada, pero ya no es una oportunidad.
 *   Con plazo previo, mientras no pase, es «abierta» (nunca «próxima»: el inicio
 *   de la BDNS es de otra fase). Solo es «cerrada» cuando pasa también el fin de la BDNS.
 */
export type Estado = 'abierta' | 'proxima' | 'sin_fecha' | 'por_verificar' | 'cerrada' | 'plazo_previo_cerrado';

export function estado(c: ConvocatoriaWeb, hoy: string): Estado {
	const fin = cierre(c);
	if (plazoPrevio(c)) {
		if (fin! >= hoy) return 'abierta';
		return c.fin && c.fin < hoy ? 'cerrada' : 'plazo_previo_cerrado';
	}
	if (fin && fin < hoy) return 'cerrada';
	if (c.inicio && c.inicio > hoy) return 'proxima';
	if (fin) return 'abierta';
	return c.publicada < restarDias(hoy, DIAS_POR_VERIFICAR) ? 'por_verificar' : 'sin_fecha';
}

/** Lo que se enseña en la lista principal */
export const enLista = (e: Estado) => e === 'abierta' || e === 'proxima' || e === 'sin_fecha';

/** De dónde sale la fecha de cierre: la ficha estructurada de la BDNS o las bases leídas */
export function origenCierre(c: ConvocatoriaWeb): 'bdns' | 'bases' | null {
	const fin = cierre(c);
	if (!fin) return null;
	return fin === c.fin ? 'bdns' : 'bases';
}

/** El plazo en una línea: «Hasta el 30 de noviembre», «Abre el 2 de enero · hasta el…», o con palabras si no hay fecha */
export function textoPlazo(c: ConvocatoriaWeb, hoy: string): string {
	const fin = cierre(c);
	const hasta = fin ? `${fechaLarga(fin)}${origenCierre(c) === 'bases' ? ' (según las bases)' : ''}` : null;
	// Plazo previo: el inicio de la BDNS es de otra fase, no se anuncia como apertura
	if (plazoPrevio(c)) return fin! < hoy ? `Plazo terminado el ${hasta}` : `Hasta el ${hasta}`;
	if (c.inicio && c.inicio > hoy) return hasta ? `Abre el ${fechaLarga(c.inicio)} · hasta el ${hasta}` : `Abre el ${fechaLarga(c.inicio)}`;
	if (hasta) return `Hasta el ${hasta}`;
	return plazoSinFecha(c) ?? 'Sin fecha de cierre confirmada';
}

/** El plazo dicho con palabras cuando no hay fecha de cierre; null si no hay nada que decir */
export function plazoSinFecha(c: ConvocatoriaWeb): string | null {
	// Si el plazo cambió y la IA aún no ha vuelto a leer las bases, su texto puede estar viejo
	const ia = c.iaRevisar ? null : c.ia?.plazo?.trim();
	return ia || c.textoFin || null;
}

/** Fecha de la última comprobación si es de hace demasiado; null si está al día o no se sabe */
export function sinComprobarDesde(c: ConvocatoriaWeb, hoy: string): string | null {
	if (!c.comprobada) return null;
	return c.comprobada < restarDias(hoy, DIAS_SIN_COMPROBAR) ? c.comprobada : null;
}

const NOMBRES: Record<string, string> = {
	titulo: 'título',
	sede: 'dónde se solicita',
	tipo: 'tipo de convocatoria',
	presupuesto: 'presupuesto',
	beneficiarios: 'quién puede pedirla',
	sectores: 'sectores',
	regiones: 'zona',
	finalidad: 'finalidad',
	inicio: 'fecha de apertura',
	fin: 'fecha de cierre',
	textoInicio: 'apertura del plazo',
	textoFin: 'cierre del plazo',
	bases: 'bases',
	documento: 'documento de las bases',
	documentos: 'documentos',
};

/** «fecha de cierre, documentos» */
export const nombresCambios = (campos: string[]) => [...new Set(campos.map((k) => NOMBRES[k] ?? k))].join(', ');
