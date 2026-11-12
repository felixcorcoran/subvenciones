/**
 * Las convocatorias de la lista, solo con lo que usa la página. Se genera al
 * construir la web y la página la pide al buscar (así no pesa al entrar).
 * Fuera, las que la lista no enseña: cerradas, «por verificar» y con el plazo
 * previo pasado (src/lib/estado.ts). Los requisitos y gastos van en
 * detalles.json, que se pide al abrir el primer «Requisitos y detalles».
 */
import type { APIRoute } from 'astro';
import datos from '../../datos/convocatorias.json';
import type { ConvocatoriaWeb, Datos } from '../lib/tipos';
import { enLista, estado } from '../lib/estado';
import { hoyMadrid } from '../lib/fechas';
import { aLista } from '../lib/web';

export const GET: APIRoute = () => {
	const { actualizado, convocatorias } = datos as Datos;
	const hoy = hoyMadrid();
	const web: ConvocatoriaWeb[] = convocatorias.filter((c) => enLista(estado(c, hoy))).map(aLista);
	return new Response(JSON.stringify({ actualizado, convocatorias: web }), {
		headers: { 'content-type': 'application/json; charset=utf-8' },
	});
};
