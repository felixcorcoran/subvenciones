/**
 * Cada mañana: las convocatorias nuevas de toda España para empresas
 * (pymes, autónomos y grandes empresas), con su ficha completa.
 *
 * Se quedan las que una empresa puede pedir. Se descartan las de concesión
 * directa (van a una entidad concreta: un convenio, una subvención
 * nominativa…) y las que ya han cerrado.
 *
 * Después se vuelve a pedir la ficha de las guardadas (una vez al día cada
 * una) para notar cambios de plazo, importe, documentos… Si la ficha no llega
 * o llega incompleta, la convocatoria se queda como estaba: un fallo de
 * consulta nunca la cierra ni borra datos.
 *
 * Solo se retiran las que han cerrado con una fecha fiable: la de la BDNS o
 * una de las bases que no la contradiga ni sea dudosa. Las que no tienen
 * fecha de cierre se quedan en los datos; la web aparta de la lista las que
 * llevan más de cuatro meses así («por verificar»).
 *
 *   DIAS_ATRAS     días hacia atrás en la primera pasada (por defecto 14)
 *   MAX_COMPROBAR  fichas guardadas que se vuelven a pedir como mucho (por defecto 200)
 */
import { buscarNuevas, ficha, type Ficha } from './bdns.ts';
import { guardarDatos, guardarVistas, hoyIso, leerDatos, leerVistas } from './almacen.ts';
import { aConvocatoria, actualizar, fichaValida } from './fichas.ts';
import { revisarCierreIa } from './lectura.ts';
import { estado } from '../src/lib/estado.ts';

const hoy = hoyIso();
const datos = leerDatos();
const vistas = leerVistas();
const guardadas = new Map(datos.convocatorias.map((c) => [c.codigo, c]));

// Desde la última pasada (con unos días de margen: hay convocatorias que se
// registran con fecha de días anteriores), o DIAS_ATRAS la primera vez
const diasAtras = Number(process.env.DIAS_ATRAS ?? 14);
const desde = new Date();
if (datos.actualizado) {
	const ultima = new Date(datos.actualizado);
	ultima.setDate(ultima.getDate() - 4);
	desde.setTime(Math.max(ultima.getTime(), Date.now() - 30 * 86_400_000));
} else {
	desde.setDate(desde.getDate() - diasAtras);
}

const encontradas = await buscarNuevas(desde, new Date());
const nuevas = encontradas.filter((r) => !guardadas.has(r.numeroConvocatoria) && !vistas[r.numeroConvocatoria]);
console.log(`${encontradas.length} convocatorias para empresas desde ${desde.toISOString().slice(0, 10)}; ${nuevas.length} sin ver`);

const descartar = (codigo: string, motivo: string) => (vistas[codigo] = { motivo, fecha: hoy });

/** La ficha, o el motivo por el que no se puede usar */
async function pedirFicha(codigo: string): Promise<Ficha | string> {
	try {
		const f = await ficha(codigo);
		return fichaValida(f, codigo) ? f : 'ficha incompleta';
	} catch (err) {
		return (err as Error).message.slice(0, 200);
	}
}

let anadidas = 0;
let nuevasSinFicha = 0;
for (const r of nuevas) {
	const f = await pedirFicha(r.numeroConvocatoria);
	if (typeof f === 'string') {
		nuevasSinFicha++;
		console.warn(`  ${r.numeroConvocatoria}: sin ficha (${f}); se reintenta mañana`);
		continue;
	}
	if (/^concesi[oó]n directa/i.test(f.tipoConvocatoria ?? '')) {
		descartar(f.codigoBDNS, 'concesión directa');
		continue;
	}
	const c = aConvocatoria(f, hoy);
	if (c.fin && c.fin < hoy) {
		descartar(c.codigo, 'cerrada');
		continue;
	}
	guardadas.set(c.codigo, c);
	anadidas++;
}

// Volver a pedir la ficha de las guardadas: primero las que hace más que no
// se comprueban. Las dos pasadas del día no la piden dos veces, y las que
// están por verificar (sin fecha de cierre y antiguas) solo una vez por semana
const maxComprobar = Number(process.env.MAX_COMPROBAR ?? 200);
const haceUnaSemana = new Date(Date.now() - 7 * 86_400_000).toISOString().slice(0, 10);
const porComprobar = [...guardadas.values()]
	.filter((c) => c.comprobada !== hoy && (estado(c, hoy) !== 'por_verificar' || !c.comprobada || c.comprobada <= haceUnaSemana))
	.sort((a, b) => (a.comprobada ?? '').localeCompare(b.comprobada ?? ''))
	.slice(0, maxComprobar);
let comprobadas = 0;
let cambiadas = 0;
let fallidas = 0;
for (const c of porComprobar) {
	const f = await pedirFicha(c.codigo);
	if (typeof f === 'string') {
		fallidas++;
		console.warn(`  ${c.codigo}: no se pudo comprobar (${f}); se queda como estaba`);
		continue;
	}
	const { convocatoria, campos, conservados } = actualizar(c, f, hoy);
	guardadas.set(c.codigo, convocatoria);
	comprobadas++;
	if (campos.length) {
		cambiadas++;
		console.log(`  ${c.codigo}: cambió ${campos.join(', ')}${convocatoria.iaRevisar ? ` (la IA la vuelve a leer: ${convocatoria.iaRevisar})` : ''}`);
	}
	if (conservados.length) console.log(`  ${c.codigo}: la ficha ya no trae ${conservados.join(', ')}; se conserva lo anterior`);
}
console.log(`${comprobadas} fichas comprobadas (${cambiadas} con cambios), ${fallidas} sin poder comprobar`);

// La fecha de cierre que la IA sacó de las bases solo se queda si no
// contradice a la BDNS y no es dudosa (también con lo leído en días anteriores)
let descartadas = 0;
for (const c of guardadas.values()) {
	if (revisarCierreIa(c)) {
		descartadas++;
		console.log(`  ${c.codigo}: fecha de cierre de las bases descartada (${c.cierreDescartado!.motivo})`);
	}
}

// Fuera solo las que han cerrado con una fecha fiable. Las que no tienen fecha
// de cierre se quedan en los datos aunque pasen los meses: la web las aparta
// de la lista como «por verificar» (src/lib/estado.ts), sin decir que han cerrado
let cerradas = 0;
for (const [codigo, c] of guardadas) {
	if (estado(c, hoy) === 'cerrada') {
		guardadas.delete(codigo);
		descartar(codigo, 'cerrada');
		cerradas++;
	}
}
const porVerificar = [...guardadas.values()].filter((c) => estado(c, hoy) === 'por_verificar').length;

// Las descartadas solo hacen falta mientras puedan volver a salir en la búsqueda
const corte = new Date(Date.now() - 45 * 86_400_000).toISOString().slice(0, 10);
for (const [codigo, v] of Object.entries(vistas)) if (v.fecha < corte) delete vistas[codigo];

datos.actualizado = new Date().toISOString();
datos.convocatorias = [...guardadas.values()];
// Para la vigilancia (scripts/salud.ts): una pasada que termina sin datos nuevos también es correcta
datos.salud = {
	...datos.salud,
	recogida: { fecha: datos.actualizado, nuevasSinFicha, fichasComprobadas: comprobadas, fichasFallidas: fallidas },
};
guardarDatos(datos);
guardarVistas(vistas);
console.log(
	`${anadidas} añadidas, ${cerradas} cerradas retiradas, ${descartadas} fechas de la IA descartadas; ${datos.convocatorias.length} guardadas (${porVerificar} por verificar)`,
);
