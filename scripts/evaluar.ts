/**
 * Evalúa cómo lee la IA las bases (scripts/evaluacion.ts).
 *
 *   npm run evaluar
 *     Lo ya leído y guardado en datos/convocatorias.json, sin gastar cupo.
 *
 *   GEMINI_API_KEY=… npm run evaluar -- --modelo gemini-3.5-flash [--n 15]
 *     Vuelve a leer con ese modelo las anotadas a mano y una muestra de las
 *     que tienen fecha de cierre o ayuntamiento en la BDNS (n, por defecto 15),
 *     sin guardar nada. Para comparar modelos antes de cambiar la cadena.
 *
 * El informe sale por pantalla y, en GitHub Actions, en el resumen.
 */
import { appendFileSync, readFileSync } from 'node:fs';
import { parseArgs } from 'node:util';
import { leerDatos } from './almacen.ts';
import { comparar, informe, verdadBdns, type Anotado, type Comparacion } from './evaluacion.ts';
import { crearLector } from './lectura-ia.ts';
import { GeminiUnavailable } from './gemini.ts';

const { values } = parseArgs({ options: { modelo: { type: 'string' }, n: { type: 'string', default: '15' } } });
const anotados = JSON.parse(readFileSync(new URL('../evaluacion/anotados.json', import.meta.url), 'utf8')) as Anotado[];
const porCodigo = new Map(anotados.map((a) => [a.codigo, a]));
const { convocatorias } = leerDatos();

let texto: string;
if (!values.modelo) {
	const filas: Comparacion[] = [];
	// La fecha que la IA dio y se apartó por dudosa (cierreDescartado) cuenta como suya: se mide la lectura, no el filtro
	for (const c of convocatorias)
		if (c.ia) filas.push(...comparar(c, { ...c.ia, plazoFin: c.cierreDescartado?.fecha ?? c.ia.plazoFin }, porCodigo.get(c.codigo)));
	texto = informe(`Lo leído y guardado (${convocatorias.filter((c) => c.ia).length} convocatorias, ${anotados.length} anotadas)`, filas);
} else {
	const apiKey = process.env.GEMINI_API_KEY;
	if (!apiKey) throw new Error('Para evaluar un modelo hace falta GEMINI_API_KEY');
	const leer = crearLector(apiKey, [values.modelo]);
	const conVerdad = convocatorias.filter((c) => c.documento && (porCodigo.has(c.codigo) || Object.keys(verdadBdns(c)).length > 0));
	// Primero las anotadas; luego, una muestra repartida de las demás (siempre la misma para un mismo día de datos)
	const resto = conVerdad.filter((c) => !porCodigo.has(c.codigo));
	const paso = Math.max(1, Math.floor(resto.length / Number(values.n)));
	const muestra = [...conVerdad.filter((c) => porCodigo.has(c.codigo)), ...resto.filter((_, i) => i % paso === 0).slice(0, Number(values.n))];
	const filas: Comparacion[] = [];
	let leidas = 0;
	for (const c of muestra) {
		try {
			const r = await leer(c);
			if (r === 'sin documento') continue;
			filas.push(...comparar(c, r.data, porCodigo.get(c.codigo)));
			leidas++;
			console.log(`  ${c.codigo} leída`);
		} catch (e) {
			if (e instanceof GeminiUnavailable) {
				console.log(`Sin cupo: ${e.message}`);
				break;
			}
			console.log(`  ${c.codigo}: ${(e as Error).message.slice(0, 160)}`);
		}
	}
	texto = informe(`${values.modelo}: ${leidas} de ${muestra.length} convocatorias leídas`, filas);
}

console.log(texto);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, texto);
