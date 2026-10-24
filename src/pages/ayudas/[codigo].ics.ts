/** El plazo de cada convocatoria con fecha de cierre, para el calendario */
import type { APIRoute, GetStaticPaths } from 'astro';
import datos from '../../../datos/convocatorias.json';
import type { Convocatoria, Datos } from '../../lib/tipos';
import { cierre } from '../../lib/encaje';
import { eventoIcs } from '../../lib/calendario';

export const getStaticPaths = (() =>
	(datos as Datos).convocatorias.filter((c) => cierre(c)).map((c) => ({ params: { codigo: c.codigo }, props: { c } }))) satisfies GetStaticPaths;

export const GET: APIRoute = ({ props, site }) => {
	const c = props.c as Convocatoria;
	const ficha = new URL(`/ayudas/${c.codigo}`, site).href;
	return new Response(eventoIcs(c, ficha), {
		headers: { 'content-type': 'text/calendar; charset=utf-8' },
	});
};
