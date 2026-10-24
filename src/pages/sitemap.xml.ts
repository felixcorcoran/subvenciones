/** La portada y la ficha de cada convocatoria abierta, para los buscadores */
import type { APIRoute } from 'astro';
import datos from '../../datos/convocatorias.json';
import type { Datos } from '../lib/tipos';

export const GET: APIRoute = ({ site }) => {
	const { actualizado, convocatorias } = datos as Datos;
	const dia = (actualizado || new Date().toISOString()).slice(0, 10);
	const urls = [
		{ loc: new URL('/', site).href, lastmod: dia },
		...convocatorias.filter((c) => !c.ia || c.ia.categoria === 'ayuda').map((c) => ({ loc: new URL(`/ayudas/${c.codigo}`, site).href, lastmod: c.publicada })),
	];
	const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map((u) => `\t<url><loc>${u.loc}</loc><lastmod>${u.lastmod}</lastmod></url>`).join('\n')}
</urlset>
`;
	return new Response(xml, { headers: { 'content-type': 'application/xml; charset=utf-8' } });
};
