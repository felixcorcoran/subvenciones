/** Requisitos y gastos de las ayudas de la lista (convocatorias.json), aparte para que la lista pese menos */
import type { APIRoute } from 'astro';
import datos from '../../datos/convocatorias.json';
import type { Datos } from '../lib/tipos';
import { enLista, estado } from '../lib/estado';
import { hoyMadrid } from '../lib/fechas';
import type { Detalles } from '../lib/web';

export const GET: APIRoute = () => {
	const hoy = hoyMadrid();
	const detalles: Detalles = {};
	for (const c of (datos as Datos).convocatorias) {
		if (c.ia && enLista(estado(c, hoy))) detalles[c.codigo] = { requisitos: c.ia.requisitos, gastos: c.ia.gastos };
	}
	return new Response(JSON.stringify(detalles), { headers: { 'content-type': 'application/json; charset=utf-8' } });
};
