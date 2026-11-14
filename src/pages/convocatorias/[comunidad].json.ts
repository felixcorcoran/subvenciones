/**
 * La lista de una comunidad (paraComunidad, lista.ts): la que pide la portada
 * con un negocio, para no descargar las ayudas de toda España.
 */
import type { APIRoute, GetStaticPaths } from 'astro';
import datos from '../../../datos/convocatorias.json';
import type { Datos } from '../../lib/tipos';
import { hoyMadrid } from '../../lib/fechas';
import { COMUNIDADES, paraComunidad } from '../../lib/lista';
import { listaWeb } from '../../lib/web';

export const getStaticPaths = (() => COMUNIDADES.map((k) => ({ params: { comunidad: k.codigo } }))) satisfies GetStaticPaths;

export const GET: APIRoute = ({ params }) => {
	const { actualizado, convocatorias } = datos as Datos;
	const lista = listaWeb(convocatorias, hoyMadrid()).filter((c) => paraComunidad(c, params.comunidad!));
	return new Response(JSON.stringify({ actualizado, convocatorias: lista }), {
		headers: { 'content-type': 'application/json; charset=utf-8' },
	});
};
