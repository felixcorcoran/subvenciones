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
 * Además, poco a poco, las anteriores al arranque del radar que siguen
 * abiertas (tramoRelleno, bdns.ts): cada pasada busca RELLENO_TRAMO días más
 * atrás, hasta RELLENO_DIAS antes del arranque.
 *
 *   DIAS_ATRAS     días hacia atrás en la primera pasada (por defecto 14)
 *   MAX_COMPROBAR  fichas guardadas que se vuelven a pedir como mucho (por defecto 400)
 *   RELLENO_DIAS   hasta cuántos días atrás se cargan las anteriores (por defecto 180; 0 para no hacerlo)
 *   RELLENO_TRAMO  días que se buscan hacia atrás en cada pasada (por defecto 10)
 */
import { buscarNuevas, ficha, inicioBusqueda, tramoRelleno, type Ficha, type ResultadoBusqueda } from './bdns.ts';
import { aCerrada, guardarCerradas, guardarDatos, guardarVistas, hoyIso, leerCerradas, leerDatos, leerVistas } from './almacen.ts';
import { aConvocatoria, actualizar, fichaValida } from './fichas.ts';
import { revisarCierreIa } from './lectura.ts';
import { estado } from '../src/lib/estado.ts';

const hoy = hoyIso();
const datos = leerDatos();
const vistas = leerVistas();
const archivo = leerCerradas();
const guardadas = new Map(datos.convocatorias.map((c) => [c.codigo, c]));

// Desde la última pasada (con margen: hay convocatorias que aparecen días
// después de su fecha de registro), o DIAS_ATRAS la primera vez
const desde = inicioBusqueda(datos.actualizado, new Date(), Number(process.env.DIAS_ATRAS ?? 14));

const encontradas = await buscarNuevas(desde, new Date());
const sinVer = (r: ResultadoBusqueda) => !guardadas.has(r.numeroConvocatoria) && !vistas[r.numeroConvocatoria];
const nuevas = encontradas.filter(sinVer);
console.log(`${encontradas.length} convocatorias para empresas desde ${desde.toISOString().slice(0, 10)}; ${nuevas.length} sin ver`);

// El relleno: un tramo más de las anteriores al arranque. Solo se da por
// buscado si la búsqueda responde; si falla, se repite en la siguiente pasada
const masAntigua = datos.convocatorias.map((c) => c.publicada).sort()[0] ?? hoy;
const tramo = Number(process.env.RELLENO_DIAS ?? 180) > 0
	? tramoRelleno(datos.relleno, hoy, masAntigua, Number(process.env.RELLENO_DIAS ?? 180), Number(process.env.RELLENO_TRAMO ?? 10))
	: null;
let delRelleno: ResultadoBusqueda[] = [];
if (tramo) {
	try {
		const vistasAhora = new Set(nuevas.map((r) => r.numeroConvocatoria));
		delRelleno = (await buscarNuevas(new Date(`${tramo.desde}T00:00:00Z`), new Date(`${tramo.hasta}T12:00:00Z`))).filter(
			(r) => sinVer(r) && !vistasAhora.has(r.numeroConvocatoria),
		);
		datos.relleno = tramo.siguiente;
		console.log(`Relleno del ${tramo.desde} al ${tramo.hasta}: ${delRelleno.length} sin ver`);
	} catch (err) {
		console.warn(`Relleno del ${tramo.desde} al ${tramo.hasta}: la búsqueda falló (${(err as Error).message.slice(0, 120)}); se repite en la siguiente pasada`);
	}
}

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
let rellenoSinFicha = 0;
const delTramo = new Set(delRelleno.map((r) => r.numeroConvocatoria));
for (const r of [...nuevas, ...delRelleno]) {
	const f = await pedirFicha(r.numeroConvocatoria);
	if (typeof f === 'string') {
		// Las del relleno no vuelven a salir en la búsqueda diaria: solo se apuntan
		if (delTramo.has(r.numeroConvocatoria)) {
			rellenoSinFicha++;
			console.warn(`  ${r.numeroConvocatoria} (relleno): sin ficha (${f})`);
		} else {
			nuevasSinFicha++;
			console.warn(`  ${r.numeroConvocatoria}: sin ficha (${f}); se reintenta mañana`);
		}
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
const maxComprobar = Number(process.env.MAX_COMPROBAR ?? 400);
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
		archivo[codigo] = aCerrada(c, hoy);
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
guardarCerradas(archivo, hoy);
console.log(
	`${anadidas} añadidas${rellenoSinFicha ? ` (${rellenoSinFicha} del relleno sin ficha)` : ''}${datos.relleno ? ` · relleno hasta el ${datos.relleno.desde}` : ''}, ${cerradas} cerradas retiradas, ${descartadas} fechas de la IA descartadas; ${datos.convocatorias.length} guardadas (${porVerificar} por verificar)`,
);
