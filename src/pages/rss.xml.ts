/** Las ayudas nuevas en RSS: otra forma de seguir el radar sin dar el email (las indexables) */
import type { APIRoute } from 'astro';
import datos from '../../datos/convocatorias.json';
import type { Datos } from '../lib/tipos';
import { hoyMadrid } from '../lib/fechas';
import { indexable } from '../lib/lista';
import { canalRss } from '../lib/rss';

export const GET: APIRoute = ({ site }) => {
	const { actualizado, convocatorias } = datos as Datos;
	const hoy = hoyMadrid();
	const cuerpo = canalRss(
		convocatorias.filter((c) => indexable(c, hoy)),
		{
			titulo: 'Radar de subvenciones: ayudas nuevas',
			descripcion: 'Las convocatorias de ayudas nuevas de toda España para autónomos y empresas, explicadas en claro.',
			enlace: new URL('/', site).href,
			propio: new URL('/rss.xml', site).href,
			actualizado,
		},
		site!,
		hoy,
	);
	return new Response(cuerpo, { headers: { 'content-type': 'application/rss+xml; charset=utf-8' } });
};
