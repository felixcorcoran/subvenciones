/**
 * Las siluetas del formulario (comunidades y provincias) y los municipios de
 * cada provincia, a partir de es-atlas (TopoJSON de los datos del Instituto
 * Geográfico Nacional). Se ejecuta a mano (npm run formas) y se sube el
 * resultado: no cambia.
 *
 * La BDNS usa códigos NUTS, que en Canarias y Baleares van por islas: las
 * islas se sacan de los polígonos de su provincia, por su posición.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { feature } from 'topojson-client';
import { geoCentroid, geoMercator, geoPath } from 'd3-geo';
import type { Feature, FeatureCollection, Geometry, MultiPolygon, Polygon } from 'geojson';

const require = createRequire(import.meta.url);
const atlas = (nombre: string) => JSON.parse(readFileSync(require.resolve(`es-atlas/es/${nombre}.json`), 'utf8'));
const capa = (nombre: string) => {
	const topo = atlas(nombre);
	return feature(topo, topo.objects[nombre]) as unknown as FeatureCollection<Geometry, { name: string }>;
};

// Código INE de comunidad → NUTS 2
const CCAA: Record<string, string> = {
	'01': 'ES61', '02': 'ES24', '03': 'ES12', '04': 'ES53', '05': 'ES70', '06': 'ES13', '07': 'ES41', '08': 'ES42', '09': 'ES51',
	'10': 'ES52', '11': 'ES43', '12': 'ES11', '13': 'ES30', '14': 'ES62', '15': 'ES22', '16': 'ES21', '17': 'ES23', '18': 'ES63', '19': 'ES64',
};
// Código INE de provincia → NUTS 3 (Baleares y las dos de Canarias, por islas)
const PROV: Record<string, string> = {
	'01': 'ES211', '02': 'ES421', '03': 'ES521', '04': 'ES611', '05': 'ES411', '06': 'ES431', '08': 'ES511', '09': 'ES412', '10': 'ES432',
	'11': 'ES612', '12': 'ES522', '13': 'ES422', '14': 'ES613', '15': 'ES111', '16': 'ES423', '17': 'ES512', '18': 'ES614', '19': 'ES424',
	'20': 'ES212', '21': 'ES615', '22': 'ES241', '23': 'ES616', '24': 'ES413', '25': 'ES513', '26': 'ES230', '27': 'ES112', '28': 'ES300',
	'29': 'ES617', '30': 'ES620', '31': 'ES220', '32': 'ES113', '33': 'ES120', '34': 'ES414', '36': 'ES114', '37': 'ES415', '39': 'ES130',
	'40': 'ES416', '41': 'ES618', '42': 'ES417', '43': 'ES514', '44': 'ES242', '45': 'ES425', '46': 'ES523', '47': 'ES418', '48': 'ES213',
	'49': 'ES419', '50': 'ES243', '51': 'ES630', '52': 'ES640',
};
/** La isla (NUTS 3) de un punto de Baleares (07) o Canarias (35, 38) */
function isla(provincia: string, [lon, lat]: [number, number]): string | null {
	if (provincia === '07') return lon > 3.6 ? 'ES533' : lon > 2.2 ? 'ES532' : 'ES531';
	if (provincia === '35') return lon < -15.2 ? 'ES705' : lat > 28.78 ? 'ES708' : 'ES704';
	if (provincia === '38') {
		if (lon > -16.98) return 'ES709';
		if (lat < 27.95) return 'ES703';
		if (lat > 28.38) return 'ES707';
		return 'ES706';
	}
	return null;
}

/** El trazo de una forma, encajada en una caja de 100 × 100 */
function trazo(f: Feature<Geometry>): string {
	const proyeccion = geoMercator().fitExtent([[4, 4], [96, 96]], f);
	return geoPath(proyeccion).digits(0)(f) ?? '';
}

const comunidades: Record<string, string> = {};
for (const f of capa('autonomous_regions').features) {
	const codigo = CCAA[String(f.id)];
	if (codigo) comunidades[codigo] = trazo(f);
}

// Polígonos de cada provincia; los de las islas, repartidos por isla
const poligonos = new Map<string, Polygon['coordinates'][]>();
for (const f of capa('provinces').features) {
	const id = String(f.id);
	const geom = f.geometry as Polygon | MultiPolygon;
	const lista = geom.type === 'Polygon' ? [geom.coordinates] : geom.coordinates;
	for (const coords of lista) {
		const centro = geoCentroid({ type: 'Polygon', coordinates: coords }) as [number, number];
		const codigo = isla(id, centro) ?? PROV[id];
		if (!codigo) continue;
		if (!poligonos.has(codigo)) poligonos.set(codigo, []);
		poligonos.get(codigo)!.push(coords);
	}
}
const provincias: Record<string, string> = {};
for (const [codigo, lista] of poligonos) {
	provincias[codigo] = trazo({ type: 'Feature', properties: {}, geometry: { type: 'MultiPolygon', coordinates: lista } });
}

// Municipios por provincia (o isla), ordenados, para el autocompletado
const municipios = new Map<string, string[]>();
for (const f of capa('municipalities').features) {
	const id = String(f.id).padStart(5, '0');
	const prov = id.slice(0, 2);
	const codigo = isla(prov, geoCentroid(f) as [number, number]) ?? PROV[prov];
	if (!codigo) continue;
	if (!municipios.has(codigo)) municipios.set(codigo, []);
	municipios.get(codigo)!.push(f.properties.name);
}
const dir = new URL('../public/municipios/', import.meta.url);
mkdirSync(dir, { recursive: true });
for (const [codigo, lista] of municipios) {
	lista.sort((a, b) => a.localeCompare(b, 'es'));
	writeFileSync(new URL(`${codigo}.json`, dir), JSON.stringify(lista));
}

writeFileSync(new URL('../src/data/formas.json', import.meta.url), JSON.stringify({ comunidades, provincias }) + '\n');
console.log(`${Object.keys(comunidades).length} comunidades, ${Object.keys(provincias).length} provincias, ${[...municipios.values()].flat().length} municipios`);
