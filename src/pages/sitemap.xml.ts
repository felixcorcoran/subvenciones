/**
 * Para los buscadores: la portada, las páginas por comunidad y por sector que
 * tienen ayudas y la ficha de cada ayuda abierta (las mismas que no llevan
 * noindex: `indexable`). La fecha de cada ficha es la de su último cambio en la
 * BDNS o, si no ha cambiado, la de su registro.
 */
import type { APIRoute } from 'astro';
import datos from '../../datos/convocatorias.json';
import type { Datos } from '../lib/tipos';
import { enLista, estado } from '../lib/estado';
import { hoyMadrid } from '../lib/fechas';
import { COMUNIDADES, deComunidad, deProvincia, indexable, PROVINCIAS, provinciasDe, SECTORES, seccionesDe } from '../lib/lista';

export const GET: APIRoute = ({ site }) => {
	const { actualizado, convocatorias } = datos as Datos;
	const hoy = hoyMadrid();
	const dia = (actualizado || new Date().toISOString()).slice(0, 10);
	const abiertas = convocatorias.filter((c) => (!c.ia || c.ia.categoria === 'ayuda') && enLista(estado(c, hoy)));
	const url = (ruta: string, lastmod: string) => ({ loc: new URL(ruta, site).href, lastmod });
	const urls = [
		url('/', dia),
		...COMUNIDADES.filter((k) => abiertas.some((c) => deComunidad(c, k.codigo))).map((k) => url(`/comunidades/${k.slug}`, dia)),
		...PROVINCIAS.filter((p) => provinciasDe(p.comunidad.codigo).length > 0 && abiertas.some((c) => deProvincia(c, p.codigo))).map((p) =>
			url(`/provincias/${p.slug}`, dia),
		),
		...SECTORES.filter((s) => abiertas.some((c) => seccionesDe(c).includes(s.letra))).map((s) => url(`/sectores/${s.slug}`, dia)),
		...convocatorias.filter((c) => indexable(c, hoy)).map((c) => url(`/ayudas/${c.codigo}`, c.cambios?.at(-1)?.fecha ?? c.publicada)),
	];
	const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map((u) => `\t<url><loc>${u.loc}</loc><lastmod>${u.lastmod}</lastmod></url>`).join('\n')}
</urlset>
`;
	return new Response(xml, { headers: { 'content-type': 'application/xml; charset=utf-8' } });
};
