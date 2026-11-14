/**
 * Lo que llega al navegador de cada convocatoria (src/pages/convocatorias.json.ts
 * y el veredicto de cada ficha): sin lo que solo usan los scripts ni lo que la
 * página no enseña, para que la primera carga en el móvil pese menos.
 */
import type { Convocatoria, ConvocatoriaWeb } from './tipos.ts';
import { enLista, estado } from './estado.ts';

export function aWeb(c: Convocatoria): ConvocatoriaWeb {
	const { documento, docs, iaModelo, iaError, iaIntentos, iaIntentadoEl, iaCompletarEl, textoInicio, cierreDescartado, cambios, ...web } = c;
	return web;
}

/**
 * Para la lista de la portada: sin requisitos ni gastos, que solo se ven al
 * abrir «Requisitos y detalles» y llegan aparte (detalles.json) la primera vez.
 */
export function aLista(c: Convocatoria): ConvocatoriaWeb {
	const web = aWeb(c);
	if (!web.ia) return web;
	// Cómo pedirla y la documentación solo se enseñan en la ficha
	const { comoPedir, documentacion, dondeSolicitar, enlaceSolicitud, ...ia } = web.ia;
	return { ...web, ia: { ...ia, requisitos: [], gastos: [] } };
}

/** Las de la lista (sin cerradas, «por verificar» ni plazo previo pasado), como llegan al navegador */
export const listaWeb = (convocatorias: Convocatoria[], hoy: string) => convocatorias.filter((c) => enLista(estado(c, hoy))).map(aLista);

/** Código → requisitos y gastos de cada ayuda de la lista */
export type Detalles = Record<string, { requisitos: string[]; gastos: string[] }>;
