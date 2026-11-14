/**
 * Un canal RSS de ayudas (el general, /rss.xml, y el de cada comunidad,
 * /comunidades/<comunidad>.xml): las registradas más recientemente primero.
 */
import type { ConvocatoriaWeb } from './tipos.ts';
import { textoPlazo } from './estado.ts';
import { titularDe } from './lista.ts';

const xml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export interface Canal {
	titulo: string;
	descripcion: string;
	/** La página del canal y la del propio RSS */
	enlace: string;
	propio: string;
	actualizado: string;
}

export function canalRss(lista: ConvocatoriaWeb[], canal: Canal, site: URL, hoy: string, max = 60): string {
	const recientes = [...lista].sort((a, b) => b.publicada.localeCompare(a.publicada) || b.codigo.localeCompare(a.codigo)).slice(0, max);
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
	return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
\t<channel>
\t\t<title>${xml(canal.titulo)}</title>
\t\t<link>${canal.enlace}</link>
\t\t<atom:link href="${canal.propio}" rel="self" type="application/rss+xml" />
\t\t<description>${xml(canal.descripcion)}</description>
\t\t<language>es-ES</language>
\t\t<lastBuildDate>${new Date(canal.actualizado || Date.now()).toUTCString()}</lastBuildDate>
${items.join('\n')}
\t</channel>
</rss>
`;
}
