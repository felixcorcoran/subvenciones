/**
 * Lo que llega al navegador de cada convocatoria (src/pages/convocatorias.json.ts
 * y el veredicto de cada ficha): sin lo que solo usan los scripts ni lo que la
 * página no enseña, para que la primera carga en el móvil pese menos.
 */
import type { Convocatoria, ConvocatoriaWeb } from './tipos.ts';

export function aWeb(c: Convocatoria): ConvocatoriaWeb {
	const { documento, docs, iaModelo, iaError, iaIntentos, iaIntentadoEl, textoInicio, cierreDescartado, cambios, ...web } = c;
	return web;
}

/**
 * Para la lista de la portada: sin requisitos ni gastos, que solo se ven al
 * abrir «Requisitos y detalles» y llegan aparte (detalles.json) la primera vez.
 */
export function aLista(c: Convocatoria): ConvocatoriaWeb {
	const web = aWeb(c);
	return web.ia ? { ...web, ia: { ...web.ia, requisitos: [], gastos: [] } } : web;
}

/** Código → requisitos y gastos de cada ayuda de la lista */
export type Detalles = Record<string, { requisitos: string[]; gastos: string[] }>;
