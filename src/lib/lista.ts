/**
 * Cómo se enseñan las ayudas en las listas de la web: el titular de cada
 * tarjeta, el orden por cercanía al negocio, la búsqueda de «Todas las
 * ayudas» y las páginas por comunidad y por sector. Es solo presentación:
 * el veredicto de cada ayuda sigue siendo el de encaje.ts (que tiene un
 * contrato con el repo de alertas y no se toca desde aquí).
 */
import type { ConvocatoriaWeb } from './tipos.ts';
import { municipioDe, normalizarMunicipio, type Motivo, type Perfil } from './encaje.ts';
import { cierre } from './fechas.ts';
import { estado } from './estado.ts';
import { SECCIONES, seccionDe } from './sectores.ts';
import provincias from '../data/provincias.json';

/** El titular corto de la IA, su resumen o, si aún no se ha leído, el título oficial */
export const titularDe = (c: ConvocatoriaWeb) => c.ia?.titular ?? c.ia?.resumen ?? c.titulo;

const sinTildes = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '');

/** «Comunidad de Madrid» → «comunidad-de-madrid»; «Illes Balears» → «illes-balears» */
export const slug = (s: string) =>
	sinTildes(s)
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, '-')
		.replace(/^-|-$/g, '');

/** Las comunidades y ciudades autónomas, con su código NUTS (ES61…) */
export const COMUNIDADES = [...new Map(provincias.map((p) => [p.codigoComunidad, { codigo: p.codigoComunidad, nombre: p.comunidad, slug: slug(p.comunidad) }])).values()].sort(
	(a, b) => a.nombre.localeCompare(b.nombre, 'es'),
);

/** Las provincias (e islas), con su comunidad */
export const PROVINCIAS = provincias
	.map((p) => ({ codigo: p.codigo, nombre: p.nombre, slug: slug(p.nombre), comunidad: COMUNIDADES.find((k) => k.codigo === p.codigoComunidad)! }))
	.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));

/** Las provincias de una comunidad (vacío si es uniprovincial: su página ya es la de la comunidad) */
export const provinciasDe = (comunidad: string) => {
	const suyas = PROVINCIAS.filter((p) => p.comunidad.codigo === comunidad);
	return suyas.length > 1 ? suyas : [];
};

/** Los sectores con página propia: los de la CNAE en los que un negocio pide ayudas */
export const SECTORES = SECCIONES.filter((s) => !['P', 'U', 'V'].includes(s.letra)).map((s) => ({
	letra: s.letra,
	nombre: s.nombre,
	slug: slug(s.nombre.replace(/\s*\(.*\)$/, '')),
}));

const zonas = (c: ConvocatoriaWeb) => c.regiones.filter((r) => r.codigo.startsWith('ES')).map((r) => r.codigo);

/** Toda España: sin más zona que «ES» */
export const esEstatal = (c: ConvocatoriaWeb) => zonas(c).includes('ES');

/** Si la ayuda es de esa comunidad (o de una provincia o comarca suya) */
export const deComunidad = (c: ConvocatoriaWeb, codigo: string) => zonas(c).some((z) => z !== 'ES' && z.startsWith(codigo));

/** Si la ayuda es de esa provincia (o de un municipio o comarca suya) */
export const deProvincia = (c: ConvocatoriaWeb, codigo: string) => zonas(c).some((z) => z.startsWith(codigo));

/** Si la ayuda es para toda la comunidad (no solo para una de sus provincias) */
export const deTodaLaComunidad = (c: ConvocatoriaWeb, codigo: string) => zonas(c).some((z) => z === codigo);

/** Las secciones de la CNAE de la ayuda; vacío si vale para cualquier sector */
export function seccionesDe(c: ConvocatoriaWeb): string[] {
	const s = [...new Set(c.sectores.map((x) => seccionDe(x.codigo)).filter((x): x is string => Boolean(x)))];
	return s.length >= 8 ? [] : s;
}

/** La comunidad de la ayuda, si es de una sola */
export function comunidadDe(c: ConvocatoriaWeb) {
	const propias = COMUNIDADES.filter((k) => deComunidad(c, k.codigo));
	return propias.length === 1 ? propias[0] : null;
}

/**
 * Lo cerca que está la ayuda del negocio, para ordenar «Revisa»: primero las
 * de su municipio, luego las de su provincia, su comunidad y las estatales;
 * al final, las de otras zonas o sin zona. Menos es más cerca.
 */
export function cercania(c: ConvocatoriaWeb, p: Perfil, municipio: string | null): number {
	if (municipio && p.municipio.trim() && normalizarMunicipio(municipio) === normalizarMunicipio(p.municipio)) return 0;
	const propias = zonas(c).filter((z) => p.provincia.startsWith(z));
	if (propias.length === 0) return 5;
	const mas = Math.max(...propias.map((z) => z.length));
	// ES617 (provincia) → 1, ES61 (comunidad) → 2, ES6 (grupo) → 3, ES → 4
	return mas >= 5 ? 1 : mas === 4 ? 2 : mas === 3 ? 3 : 4;
}

/** Ordena por cercanía y, a igual cercanía, por la fecha de cierre (sin fecha, al final) */
export const porCierre = (a: ConvocatoriaWeb, b: ConvocatoriaWeb) => (cierre(a) ?? '9999').localeCompare(cierre(b) ?? '9999');

/**
 * El texto de un motivo tal como se enseña. La BDNS marca para toda España
 * algunas ayudas de ayuntamientos: «Para toda España» junto a «Es de Cabra»
 * se contradecía, así que se explica.
 */
export function textoMotivo(m: Motivo, municipio: string | null): string {
	if (m.texto === 'Para toda España' && municipio) return 'La BDNS la marca para toda España, pero la convoca un ayuntamiento';
	return m.texto;
}

/** Si la ayuda contiene todas las palabras buscadas (sin tildes ni mayúsculas) */
export function coincide(c: ConvocatoriaWeb, busqueda: string): boolean {
	const palabras = sinTildes(busqueda).toLowerCase().split(/\s+/).filter(Boolean);
	if (!palabras.length) return true;
	const texto = sinTildes(
		[
			c.titulo,
			c.ia?.titular,
			c.ia?.resumen,
			c.ia?.paraQuien,
			c.organo.nivel2,
			c.organo.nivel3,
			c.codigo,
			// Buscar por municipio: el del ayuntamiento que convoca y el que exigen las bases
			municipioDe(c),
			c.ia?.condiciones.municipio,
			...c.regiones.map((r) => r.nombre),
		]
			.filter(Boolean)
			.join(' '),
	).toLowerCase();
	return palabras.every((w) => texto.includes(w));
}

/** Días hasta el cierre (0 si cierra hoy), o null si no tiene fecha */
export function diasHasta(c: ConvocatoriaWeb, hoy: string): number | null {
	const fin = cierre(c);
	return fin ? Math.round((Date.parse(`${fin}T00:00:00Z`) - Date.parse(`${hoy}T00:00:00Z`)) / 86_400_000) : null;
}

/**
 * Si la ficha va a los buscadores (y al sitemap y al RSS): solo las ayudas que
 * siguen siendo una oportunidad. Fuera: premios y otras, «por verificar» y
 * con el plazo previo ya pasado.
 */
export function indexable(c: ConvocatoriaWeb, hoy: string): boolean {
	const e = estado(c, hoy);
	return (!c.ia || c.ia.categoria === 'ayuda') && e !== 'por_verificar' && e !== 'plazo_previo_cerrado' && e !== 'cerrada';
}

/** Las ayudas de la lista (sin premios ni otras, sin cerradas ni «por verificar») */
export const ayudasAbiertas = <T extends ConvocatoriaWeb>(lista: T[], hoy: string): T[] =>
	lista.filter((c) => (!c.ia || c.ia.categoria === 'ayuda') && ['abierta', 'proxima', 'sin_fecha'].includes(estado(c, hoy)));
