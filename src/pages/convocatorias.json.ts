/**
 * Las convocatorias abiertas, solo con lo que usa la página. Se genera al
 * construir la web y la página la pide al buscar (así no pesa al entrar).
 */
import type { APIRoute } from 'astro';
import datos from '../../datos/convocatorias.json';
import type { ConvocatoriaWeb, Datos } from '../lib/tipos';

export const GET: APIRoute = () => {
	const { actualizado, convocatorias } = datos as Datos;
	const web: ConvocatoriaWeb[] = convocatorias.map(({ documento, docs, iaModelo, iaError, iaIntentos, iaIntentadoEl, textoInicio, cierreDescartado, ...c }) => c);
	return new Response(JSON.stringify({ actualizado, convocatorias: web }), {
		headers: { 'content-type': 'application/json; charset=utf-8' },
	});
};
