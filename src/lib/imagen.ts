/**
 * La imagen al compartir cada ficha (1200×630): quién convoca, el titular,
 * el plazo y el importe, con la tipografía y los colores de la web. Se genera
 * al construir con Satori (de HTML a SVG) y resvg (a PNG), sin servicios.
 */
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import satori from 'satori';
import { Resvg } from '@resvg/resvg-js';

const require = createRequire(import.meta.url);
const fuente = (peso: number) => readFileSync(require.resolve(`@fontsource/sora/files/sora-latin-${peso}-normal.woff`));
let fuentes: { name: string; data: Buffer; weight: 400 | 500; style: 'normal' }[] | null = null;

type Nodo = { type: string; props: { style?: Record<string, unknown>; children?: unknown } };
const h = (type: string, style: Record<string, unknown>, children?: unknown): Nodo => ({ type, props: { style, children } });

export interface DatosImagen {
	organo: string;
	titular: string;
	plazo: string;
	importe: string | null;
}

export async function imagenFicha(d: DatosImagen): Promise<Buffer> {
	fuentes ??= [
		{ name: 'Sora', data: fuente(400), weight: 400, style: 'normal' },
		{ name: 'Sora', data: fuente(500), weight: 500, style: 'normal' },
	];
	const titular = d.titular.length > 110 ? `${d.titular.slice(0, 107).trimEnd()}…` : d.titular;
	const organo = d.organo.length > 70 ? `${d.organo.slice(0, 67).trimEnd()}…` : d.organo;
	const chip = (texto: string, fondo: string) =>
		h('div', { display: 'flex', padding: '12px 26px', borderRadius: 999, background: fondo, fontSize: 28, color: '#171717' }, texto);
	const arbol = h(
		'div',
		{ width: 1200, height: 630, display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '64px 72px', background: '#FAF9F6', fontFamily: 'Sora' },
		[
			h('div', { display: 'flex', flexDirection: 'column' }, [
				h('div', { display: 'flex', fontSize: 24, fontWeight: 500, letterSpacing: 3, color: '#6B6B6B', textTransform: 'uppercase' }, organo),
				h('div', { display: 'flex', marginTop: 28, fontSize: titular.length > 70 ? 50 : 60, lineHeight: 1.12, letterSpacing: -1.5, color: '#171717' }, titular),
			]),
			h('div', { display: 'flex', flexDirection: 'column' }, [
				h('div', { display: 'flex', gap: 16 }, [chip(d.plazo.length > 60 ? `${d.plazo.slice(0, 57)}…` : d.plazo, '#FFD6CC'), ...(d.importe ? [chip(d.importe, 'rgba(23,23,23,0.06)')] : [])]),
				h('div', { display: 'flex', justifyContent: 'space-between', marginTop: 40, fontSize: 24, color: '#6B6B6B' }, [
					h('div', { display: 'flex', color: '#171717', fontWeight: 500 }, 'Radar de subvenciones'),
					h('div', { display: 'flex' }, 'subvenciones.felixcorcoran.es'),
				]),
			]),
		],
	);
	const svg = await satori(arbol as never, { width: 1200, height: 630, fonts: fuentes });
	return new Resvg(svg, { fitTo: { mode: 'width', value: 1200 } }).render().asPng();
}
