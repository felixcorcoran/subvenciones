/**
 * Los plazos de las ayudas abiertas de cada comunidad (y las de toda España)
 * como calendario al que suscribirse: se actualiza solo con las nuevas.
 */
import type { APIRoute, GetStaticPaths } from 'astro';
import datos from '../../../datos/convocatorias.json';
import type { Datos } from '../../lib/tipos';
import { hoyMadrid } from '../../lib/fechas';
import { ayudasAbiertas, COMUNIDADES, deComunidad, esEstatal } from '../../lib/lista';
import { calendarioIcs } from '../../lib/calendario';

export const getStaticPaths = (() => COMUNIDADES.map((k) => ({ params: { slug: k.slug }, props: { k } }))) satisfies GetStaticPaths;

export const GET: APIRoute = ({ props, site }) => {
	const { k } = props as { k: (typeof COMUNIDADES)[number] };
	const lista = ayudasAbiertas((datos as Datos).convocatorias, hoyMadrid()).filter((c) => deComunidad(c, k.codigo) || esEstatal(c));
	const cuerpo = calendarioIcs(lista, (c) => new URL(`/ayudas/${c.codigo}`, site).href, `Plazos de ayudas · ${k.nombre}`);
	return new Response(cuerpo, { headers: { 'content-type': 'text/calendar; charset=utf-8' } });
};
