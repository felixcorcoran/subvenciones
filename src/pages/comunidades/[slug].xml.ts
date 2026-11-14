/** Las ayudas nuevas de cada comunidad (y las de toda España) en RSS */
import type { APIRoute, GetStaticPaths } from 'astro';
import datos from '../../../datos/convocatorias.json';
import type { Datos } from '../../lib/tipos';
import { hoyMadrid } from '../../lib/fechas';
import { COMUNIDADES, deComunidad, esEstatal, indexable } from '../../lib/lista';
import { canalRss } from '../../lib/rss';

export const getStaticPaths = (() => COMUNIDADES.map((k) => ({ params: { slug: k.slug }, props: { k } }))) satisfies GetStaticPaths;

export const GET: APIRoute = ({ props, site }) => {
	const { k } = props as { k: (typeof COMUNIDADES)[number] };
	const { actualizado, convocatorias } = datos as Datos;
	const hoy = hoyMadrid();
	const cuerpo = canalRss(
		convocatorias.filter((c) => indexable(c, hoy) && (deComunidad(c, k.codigo) || esEstatal(c))),
		{
			titulo: `Radar de subvenciones: ayudas nuevas en ${k.nombre}`,
			descripcion: `Las convocatorias de ayudas nuevas para negocios de ${k.nombre} y las de toda España, explicadas en claro.`,
			enlace: new URL(`/comunidades/${k.slug}`, site).href,
			propio: new URL(`/comunidades/${k.slug}.xml`, site).href,
			actualizado,
		},
		site!,
		hoy,
	);
	return new Response(cuerpo, { headers: { 'content-type': 'application/rss+xml; charset=utf-8' } });
};
