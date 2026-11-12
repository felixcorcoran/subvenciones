/**
 * Las ayudas nuevas en RSS: otra forma de seguir el radar sin dar el email.
 * Las 60 registradas más recientemente que siguen abiertas (las indexables).
 */
import type { APIRoute } from 'astro';
import datos from '../../datos/convocatorias.json';
import type { Datos } from '../lib/tipos';
import { textoPlazo } from '../lib/estado';
import { hoyMadrid } from '../lib/fechas';
import { indexable, titularDe } from '../lib/lista';

const xml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export const GET: APIRoute = ({ site }) => {
	const { actualizado, convocatorias } = datos as Datos;
	const hoy = hoyMadrid();
	const recientes = convocatorias
		.filter((c) => indexable(c, hoy))
		.sort((a, b) => b.publicada.localeCompare(a.publicada) || b.codigo.localeCompare(a.codigo))
		.slice(0, 60);
	const items = recientes.map((c) => {
		const enlace = new URL(`/ayudas/${c.codigo}`, site).href;
		const descripcion = [c.ia?.resumen, textoPlazo(c, hoy), [c.organo.nivel3, c.organo.nivel2].filter(Boolean).join(' — ')].filter(Boolean).join(' · ');
		return `\t\t<item>
\t\t\t<title>${xml(titularDe(c))}</title>
\t\t\t<link>${enlace}</link>
\t\t\t<guid isPermaLink="true">${enlace}</guid>
\t\t\t<pubDate>${new Date(`${c.publicada}T08:00:00Z`).toUTCString()}</pubDate>
\t\t\t<description>${xml(descripcion)}</description>
\t\t</item>`;
	});
	const cuerpo = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
\t<channel>
\t\t<title>Radar de subvenciones: ayudas nuevas</title>
\t\t<link>${new URL('/', site).href}</link>
\t\t<atom:link href="${new URL('/rss.xml', site).href}" rel="self" type="application/rss+xml" />
\t\t<description>Las convocatorias de ayudas nuevas de toda España para autónomos y empresas, explicadas en claro.</description>
\t\t<language>es-ES</language>
\t\t<lastBuildDate>${new Date(actualizado || Date.now()).toUTCString()}</lastBuildDate>
${items.join('\n')}
\t</channel>
</rss>
`;
	return new Response(cuerpo, { headers: { 'content-type': 'application/rss+xml; charset=utf-8' } });
};
