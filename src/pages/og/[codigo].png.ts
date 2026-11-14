/** La imagen al compartir de cada ficha (src/lib/imagen.ts) */
import type { APIRoute, GetStaticPaths } from 'astro';
import datos from '../../../datos/convocatorias.json';
import type { Convocatoria, Datos } from '../../lib/tipos';
import { municipioDe } from '../../lib/encaje';
import { textoPlazo } from '../../lib/estado';
import { hoyMadrid } from '../../lib/fechas';
import { titularDe } from '../../lib/lista';
import { imagenFicha } from '../../lib/imagen';

export const getStaticPaths = (() => (datos as Datos).convocatorias.map((c) => ({ params: { codigo: c.codigo }, props: { c } }))) satisfies GetStaticPaths;

const euros = (n: number) => n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });

export const GET: APIRoute = async ({ props }) => {
	const c = props.c as Convocatoria;
	const png = await imagenFicha({
		organo: [c.organo.nivel3, municipioDe(c) ?? c.organo.nivel2].filter((v, i, a) => v && a.indexOf(v) === i).join(' · '),
		titular: titularDe(c),
		plazo: textoPlazo(c, hoyMadrid()),
		importe: c.ia?.importeMaximo ? `Hasta ${euros(c.ia.importeMaximo)}` : c.presupuesto ? `Presupuesto: ${euros(c.presupuesto)}` : null,
	});
	return new Response(new Uint8Array(png), { headers: { 'content-type': 'image/png' } });
};
