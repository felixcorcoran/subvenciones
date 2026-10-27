/**
 * Si la recogida diaria necesita atención, a partir de lo que dejan apuntado
 * recoger.ts y analizar.ts en `datos.salud` y de la fecha de la última
 * recogida completa. Lo usa la Action de vigilancia (scripts/vigilar.ts).
 *
 * Distingue:
 * - Fallo de la ejecución: no hay una recogida completa en más de 30 horas
 *   (hay dos pasadas al día, así que hace falta que fallen varias seguidas).
 * - Fallo parcial de una fuente: la BDNS no devuelve muchas fichas, o la IA
 *   lleva días sin leer nada teniendo bases pendientes.
 * - Ejecución correcta sin cambios: no es un problema.
 */
import type { Datos } from '../src/lib/tipos.ts';

export const HORAS_SIN_RECOGIDA = 30;
export const DIAS_SIN_LECTURA = 3;
/** Fichas sin poder comprobar a partir de las que se avisa (y más de la mitad de las pedidas) */
export const MIN_FICHAS_FALLIDAS = 5;

export interface Problema {
	tipo: 'ejecucion' | 'bdns' | 'ia';
	texto: string;
}

const dia = (iso: string) => iso.slice(0, 10);

export function diagnostico(datos: Datos, ahora = new Date()): Problema[] {
	const problemas: Problema[] = [];
	const ultima = datos.actualizado ? new Date(datos.actualizado) : null;
	if (!ultima || Number.isNaN(ultima.getTime())) {
		problemas.push({ tipo: 'ejecucion', texto: 'No consta ninguna recogida completa.' });
	} else if (ahora.getTime() - ultima.getTime() > HORAS_SIN_RECOGIDA * 3_600_000) {
		problemas.push({
			tipo: 'ejecucion',
			texto: `La última recogida completa fue el ${dia(datos.actualizado)}: las siguientes han fallado o no se han ejecutado. Mira la Action «Recoger convocatorias».`,
		});
	}

	const r = datos.salud?.recogida;
	if (r) {
		const pedidas = r.fichasComprobadas + r.fichasFallidas;
		if (r.fichasFallidas >= MIN_FICHAS_FALLIDAS && r.fichasFallidas * 2 > pedidas) {
			problemas.push({
				tipo: 'bdns',
				texto: `En la recogida del ${dia(r.fecha)}, la BDNS no devolvió ${r.fichasFallidas} de ${pedidas} fichas guardadas. Se quedan como estaban y se reintentan en la siguiente pasada.`,
			});
		}
		if (r.nuevasSinFicha >= MIN_FICHAS_FALLIDAS) {
			problemas.push({ tipo: 'bdns', texto: `En la recogida del ${dia(r.fecha)}, ${r.nuevasSinFicha} convocatorias nuevas se quedaron sin ficha (se reintentan).` });
		}
	}

	const l = datos.salud?.lectura;
	if (l && l.pendientes > 0) {
		const desde = l.ultimaLectura;
		const limite = new Date(ahora.getTime() - DIAS_SIN_LECTURA * 86_400_000).toISOString().slice(0, 10);
		// Sin ninguna lectura apuntada, se cuenta desde la primera pasada con vigilancia
		if ((desde ?? dia(l.fecha)) < limite) {
			problemas.push({
				tipo: 'ia',
				texto: `La IA no lee bases desde ${desde ? `el ${desde}` : 'que hay vigilancia'} y hay ${l.pendientes} pendientes. Mira el paso «npm run analizar» (cupo de Gemini, modelos, clave).`,
			});
		}
	}
	return problemas;
}
