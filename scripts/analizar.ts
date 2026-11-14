/**
 * Lee las bases de cada convocatoria nueva con Gemini y saca, en lenguaje
 * claro, qué financia, para quién, cuánto, el plazo y los requisitos; y las
 * condiciones que usa el formulario para decir si un negocio encaja.
 *
 * Solo lo que dice el documento: si algo no consta, se queda como «no consta».
 * Si Gemini no tiene cupo, lo pendiente sigue mañana.
 *
 *   GEMINI_API_KEY  clave de Google AI Studio (proyecto sin facturación)
 *   GEMINI_MODELS   cadena de modelos, separados por comas
 *   MAX_ANALIZAR    cuántas como mucho en cada pasada (por defecto 40)
 *   MAX_MINUTOS     minutos como mucho (por defecto 35), para que la Action suba lo leído
 *   MAX_COMPLETAR   ya leídas que se vuelven a leer como mucho para completarlas (por defecto 15)
 *   MAX_TITULARES   titulares y finalidades que se piden como mucho por pasada para las ya leídas (por defecto 60)
 *
 * Detrás de las pendientes, las ya leídas antes de que se pidiera cómo
 * pedirla y la documentación se vuelven a leer poco a poco (porCompletar,
 * lectura.ts); si falla, lo leído se queda. Al final, las que no tienen
 * titular corto o finalidades los reciben a partir de su resumen y sus
 * gastos (sin volver a leer el PDF): una sola llamada de texto.
 */
import { GeminiUnavailable, generateJson } from './gemini.ts';
import { guardarDatos, hoyIso, leerDatos } from './almacen.ts';
import { limpiarMunicipio, porCompletar, porLeer, revisarCierreIa, titularValido } from './lectura.ts';
import { crearLector } from './lectura-ia.ts';
import { CLAVES_FINALIDAD, limpiarFinalidades } from '../src/lib/finalidades.ts';
import { cierre } from '../src/lib/fechas.ts';
import { estado } from '../src/lib/estado.ts';
import type { Analisis, Convocatoria } from '../src/lib/tipos.ts';

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
	console.log('Sin GEMINI_API_KEY: no se leen bases en esta pasada');
	process.exit(0);
}
const models = (process.env.GEMINI_MODELS ?? 'gemini-3.8-flash,gemini-3.6-flash,gemini-3.7-flash,gemini-3.5-flash,gemini-3.5-flash-lite').split(',').map((m) => m.trim());
const max = Number(process.env.MAX_ANALIZAR ?? 40);
// Tope de tiempo: la Action tiene que llegar a subir lo leído
const hasta = Date.now() + Number(process.env.MAX_MINUTOS ?? 35) * 60_000;

const datos = leerDatos();
const hoy = hoyIso();
const pendientes = datos.convocatorias
	.filter((c) => porLeer(c, hoy))
	// Primero las que nunca han fallado; luego las que siguen en la lista (no las
	// «por verificar») y, entre ellas, las que cierran antes
	.sort(
		(a, b) =>
			(a.iaIntentos ?? 0) - (b.iaIntentos ?? 0) ||
			Number(estado(a, hoy) === 'por_verificar') - Number(estado(b, hoy) === 'por_verificar') ||
			(cierre(a) ?? '9999').localeCompare(cierre(b) ?? '9999'),
	)
	.slice(0, max);
// Detrás, con lo que sobre de la pasada: completar las ya leídas
const completar = datos.convocatorias
	.filter((c) => porCompletar(c, hoy))
	.sort((a, b) => (cierre(a) ?? '9999').localeCompare(cierre(b) ?? '9999'))
	.slice(0, Math.max(0, Math.min(Number(process.env.MAX_COMPLETAR ?? 15), max - pendientes.length)));
console.log(`${pendientes.length} convocatorias por leer y ${completar.length} por completar`);

const leer = crearLector(apiKey, models);

/** Lo leído pasa a la convocatoria, con las mismas reglas que una lectura nueva */
function aplicar(c: Convocatoria, data: Analisis, model: string) {
	c.ia = data;
	c.iaModelo = model;
	// «Cataluña» no es un municipio (limpiarMunicipio, lectura.ts)
	limpiarMunicipio(c);
	// La fecha de cierre leída solo se queda si no contradice a la BDNS y no es dudosa
	delete c.cierreDescartado;
	if (revisarCierreIa(c)) console.log(`  ${c.codigo}: fecha de cierre de las bases descartada (${c.cierreDescartado!.motivo})`);
	delete c.iaRevisar;
	delete c.iaError;
	delete c.iaIntentos;
	delete c.iaIntentadoEl;
	delete c.iaCompletarEl;
}

let leidas = 0;
let fallidas = 0;
let completadas = 0;
let completarIntentadas = 0;
let completarFallidas = 0;
let sinCupo = false;
const cola = [...pendientes.map((c) => ({ c, completando: false })), ...completar.map((c) => ({ c, completando: true }))];
for (const { c, completando } of cola) {
	if (Date.now() > hasta) {
		console.log('Tiempo agotado: lo pendiente sigue mañana');
		break;
	}
	try {
		if (completando) completarIntentadas++;
		const r = await leer(c);
		if (r === 'sin documento') continue;
		aplicar(c, r.data, r.model);
		if (completando) completadas++;
		else leidas++;
		console.log(`  ${c.codigo} ${completando ? 'completada' : 'leída'} (${r.model}): ${r.data.categoria} · ${r.data.resumen.slice(0, 80)}`);
	} catch (err) {
		if (err instanceof GeminiUnavailable) {
			console.log(`Gemini no disponible: ${err.message}. Lo pendiente sigue mañana.`);
			sinCupo = true;
			break;
		}
		if (completando) {
			// Completar no es leer: lo leído se queda, sin error, y se reintenta pasada la espera
			c.iaCompletarEl = hoy;
			completarFallidas++;
			console.warn(`  ${c.codigo}: no se pudo completar (${(err as Error).message.slice(0, 160)}); lo leído se queda`);
		} else {
			// Si ya estaba leída (relectura), lo leído antes se queda
			c.iaIntentos = (c.iaIntentos ?? 0) + 1;
			c.iaIntentadoEl = hoy;
			c.iaError = (err as Error).message.slice(0, 300);
			fallidas++;
			console.warn(`  ${c.codigo}: ${c.iaError}`);
		}
	}
	guardarDatos(datos); // tras cada una: si la pasada se corta, no se pierde lo leído
}

// Titular corto y finalidades para las ya leídas que no los tienen: solo a
// partir de su resumen y sus gastos, sin añadir nada
const sinCompletar = datos.convocatorias
	.filter((c) => c.ia && !c.iaRevisar && (!c.ia.titular || c.ia.finalidades === undefined))
	.slice(0, Number(process.env.MAX_TITULARES ?? 60));
if (sinCompletar.length && !sinCupo && Date.now() < hasta) {
	try {
		const { data, model } = await generateJson<{ codigo: string; titular: string; finalidades: string[] }[]>(
			{ apiKey, models, minIntervalMs: 6_000 },
			`Recibes resúmenes de convocatorias de ayudas públicas españolas, con en qué se puede gastar cada una. Para cada una:
- titular: de 40 a 70 caracteres, qué financia, sin punto final, en español claro.
- finalidades: para qué sirve, de esta lista (una o varias; vacía si ninguna encaja): ${CLAVES_FINALIDAD.join(', ')}. empezar = darse de alta o montar un negocio; contratar = contratar o mantener empleo; digitalizar = web, software, comercio electrónico; invertir = equipos, maquinaria, vehículos u obras; local = alquiler, compra o reforma del local; energia = eficiencia energética, renovables; innovar = innovación e I+D; exportar = ferias, internacionalización, promoción; formacion = formación; liquidez = gastos corrientes, cuotas o liquidez.
Usa SOLO lo que dicen el resumen, los gastos y quién convoca; no añadas datos. Devuelve el mismo código de cada una.`,
			[{ text: JSON.stringify(sinCompletar.map((c) => ({ codigo: c.codigo, convoca: c.organo.nivel3, resumen: c.ia!.resumen, gastos: c.ia!.gastos }))) }],
			{
				type: 'ARRAY',
				items: {
					type: 'OBJECT',
					properties: { codigo: { type: 'STRING' }, titular: { type: 'STRING' }, finalidades: { type: 'ARRAY', items: { type: 'STRING', enum: CLAVES_FINALIDAD } } },
					required: ['codigo', 'titular', 'finalidades'],
				},
			},
		);
		const porCodigo = new Map(sinCompletar.map((c) => [c.codigo, c]));
		let titulares = 0;
		let conFinalidad = 0;
		for (const x of Array.isArray(data) ? data : []) {
			const c = porCodigo.get(String(x?.codigo));
			if (!c?.ia) continue;
			const titular = titularValido(typeof x?.titular === 'string' ? x.titular : undefined);
			if (!c.ia.titular && titular) {
				c.ia.titular = titular;
				titulares++;
			}
			if (c.ia.finalidades === undefined && Array.isArray(x?.finalidades)) {
				c.ia.finalidades = limpiarFinalidades(x.finalidades);
				conFinalidad++;
			}
		}
		console.log(`${titulares} titulares cortos y ${conFinalidad} finalidades para las ya leídas (${model})`);
		guardarDatos(datos);
	} catch (err) {
		// No es grave: la web usa el resumen y no ordena por finalidad mientras tanto
		console.log(`Titulares y finalidades: ${(err as Error).message.slice(0, 160)}; se intenta en la siguiente pasada`);
	}
}

// Para la vigilancia (scripts/salud.ts): si la IA lleva días sin leer nada con bases pendientes
datos.salud = {
	...datos.salud,
	lectura: {
		fecha: new Date().toISOString(),
		pendientes: datos.convocatorias.filter((c) => porLeer(c, hoy)).length,
		leidas,
		fallidas,
		ultimaLectura: leidas > 0 ? hoy : (datos.salud?.lectura?.ultimaLectura ?? null),
		conError: datos.convocatorias.filter((c) => c.iaError).length,
		completarIntentadas,
		completarFallidas,
		// La racha sin cupo empieza el primer día que falta y se corta en cuanto hay
		sinCupoDesde: sinCupo ? (datos.salud?.lectura?.sinCupoDesde ?? hoy) : null,
	},
};
guardarDatos(datos);
console.log(`${leidas} leídas, ${completadas} completadas, ${fallidas} con error`);
