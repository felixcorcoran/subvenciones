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
 *   MAX_TITULARES   titulares cortos que se piden como mucho por pasada para las ya leídas (por defecto 60)
 *
 * Al final, las leídas antes de que existiera el titular corto lo reciben a
 * partir de su resumen (sin volver a leer el PDF): una sola llamada de texto.
 */
import { documento } from './bdns.ts';
import { GeminiUnavailable, generateJson } from './gemini.ts';
import { guardarDatos, hoyIso, leerDatos } from './almacen.ts';
import { analisisValido, porLeer, revisarCierreIa, titularValido } from './lectura.ts';
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
const MAX_BYTES = 18 * 1024 * 1024;

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
console.log(`${pendientes.length} convocatorias por leer`);

const SISTEMA = `Lees convocatorias de ayudas públicas españolas y las explicas al dueño de un pequeño negocio o a un autónomo, que no sabe de jerga administrativa.

Reglas:
- Usa SOLO lo que dice el documento. Si un dato no aparece, pon "no_consta" o null. No supongas ni completes con lo habitual.
- Español claro y directo, frases cortas, sin jerga («beneficiario», «ex post», «acreditar» → dilo con palabras normales).
- categoria: "ayuda" si es dinero (o una ventaja) que un negocio puede pedir para su actividad; "premio" si es un concurso o premio; "otro" si es otra cosa (seleccionar una entidad colaboradora, becas para personas, etc.).
- titular: qué financia en un titular de 40 a 70 caracteres, sin punto final, con quién convoca o la zona si cabe («Ayudas al alquiler de locales en Cabra», «Bonos para digitalizar comercios de Aragón»).
- requisitos: los que de verdad deciden si alguien puede pedirla (dónde tiene que estar el negocio, qué tipo de negocio, antigüedad, estar al corriente de pagos…). Como mucho 8, cada uno en una frase corta.
- gastos: en qué se puede gastar la ayuda. Lista vacía si no lo dice o no aplica.
- importe: lo que puede recibir cada negocio («Hasta 3.000 € por negocio», «El 50 % de la inversión, con un máximo de 10.000 €»). Si solo indica el presupuesto total, dilo así. importeMaximo: el máximo por negocio en euros, o null.
- plazo: cuándo se puede pedir, dicho claro. plazoFin: la fecha de cierre AAAA-MM-DD solo si se puede saber con certeza a partir del documento y de la fecha de publicación que te doy; si depende de una publicación cuya fecha no sabes, null.
- condiciones.autonomos / sociedades: "si" si pueden pedirla, "no" si quedan fuera, "no_consta" si no lo dice.
- condiciones.emprendedores: "si" si pueden pedirla personas que aún no han empezado su negocio (por ejemplo, ayudas para darse de alta), "no" si hay que tener ya la actividad en marcha, "no_consta" si no lo dice.
- condiciones.maxEmpleados: el máximo de trabajadores permitido, o null. antiguedadMinMeses: meses mínimos de actividad, o null. antiguedadMaxMeses: si solo vale para negocios creados hace menos de X meses, esos meses; si no, null.
- condiciones.municipio: si el negocio tiene que estar en un municipio concreto, su nombre; si no, null.
- dudas: una frase corta con lo importante que no queda claro, o null.`;

const SINO = { type: 'STRING', enum: ['si', 'no', 'no_consta'] };
const ESQUEMA = {
	type: 'OBJECT',
	properties: {
		categoria: { type: 'STRING', enum: ['ayuda', 'premio', 'otro'] },
		resumen: { type: 'STRING' },
		titular: { type: 'STRING' },
		paraQuien: { type: 'STRING' },
		importe: { type: 'STRING' },
		importeMaximo: { type: 'NUMBER', nullable: true },
		plazo: { type: 'STRING' },
		plazoFin: { type: 'STRING', nullable: true },
		requisitos: { type: 'ARRAY', items: { type: 'STRING' } },
		gastos: { type: 'ARRAY', items: { type: 'STRING' } },
		condiciones: {
			type: 'OBJECT',
			properties: {
				autonomos: SINO,
				sociedades: SINO,
				emprendedores: SINO,
				maxEmpleados: { type: 'INTEGER', nullable: true },
				antiguedadMinMeses: { type: 'INTEGER', nullable: true },
				antiguedadMaxMeses: { type: 'INTEGER', nullable: true },
				municipio: { type: 'STRING', nullable: true },
			},
			required: ['autonomos', 'sociedades', 'emprendedores', 'maxEmpleados', 'antiguedadMinMeses', 'antiguedadMaxMeses', 'municipio'],
		},
		dudas: { type: 'STRING', nullable: true },
	},
	required: ['categoria', 'resumen', 'titular', 'paraQuien', 'importe', 'importeMaximo', 'plazo', 'plazoFin', 'requisitos', 'gastos', 'condiciones', 'dudas'],
};

function contexto(c: Convocatoria): string {
	return [
		`Convocatoria BDNS ${c.codigo}: ${c.titulo}`,
		`Convoca: ${[c.organo.nivel3, c.organo.nivel2].filter(Boolean).join(' — ')}`,
		`Registrada en la BDNS el ${c.publicada}.`,
		c.fin ? `Fin de solicitud según la BDNS: ${c.fin}.` : '',
		c.textoInicio ? `Inicio del plazo (texto de la BDNS): ${c.textoInicio}` : '',
		c.textoFin ? `Fin del plazo (texto de la BDNS): ${c.textoFin}` : '',
		'Explica el documento adjunto siguiendo las reglas.',
	]
		.filter(Boolean)
		.join('\n');
}

const valida = (f: string | null) => (f && /^\d{4}-\d{2}-\d{2}$/.test(f) ? f : null);

let leidas = 0;
let fallidas = 0;
let sinCupo = false;
for (const c of pendientes) {
	if (Date.now() > hasta) {
		console.log('Tiempo agotado: lo pendiente sigue mañana');
		break;
	}
	const doc = c.documento!;
	let bytes: Buffer | null = null;
	if (/\.pdf$/i.test(doc.nombre)) {
		try {
			bytes = await documento(doc.id);
		} catch (err) {
			// La BDNS no ha dado el documento: es un fallo de consulta, no cuenta como intento
			console.warn(`  ${c.codigo}: sin documento (${(err as Error).message.slice(0, 120)}); se reintenta en la próxima pasada`);
			continue;
		}
	}
	try {
		if (!bytes) throw new Error(`no es un PDF (${doc.nombre})`);
		if (bytes.length > MAX_BYTES) throw new Error(`documento demasiado grande (${Math.round(bytes.length / 1e6)} MB)`);
		const { data, model } = await generateJson<Analisis>(
			{ apiKey, models, minIntervalMs: 6_000 },
			SISTEMA,
			[{ inlineData: { mimeType: 'application/pdf', data: bytes.toString('base64') } }, { text: contexto(c) }],
			ESQUEMA,
		);
		// Una respuesta a medias no sustituye a nada: cuenta como intento fallido
		if (!analisisValido(data)) throw new Error(`respuesta incompleta de ${model}`);
		data.plazoFin = valida(data.plazoFin);
		const titular = titularValido(data.titular);
		if (titular) data.titular = titular;
		else delete data.titular;
		c.ia = data;
		c.iaModelo = model;
		// La fecha de cierre leída solo se queda si no contradice a la BDNS y no es dudosa
		delete c.cierreDescartado;
		if (revisarCierreIa(c)) console.log(`  ${c.codigo}: fecha de cierre de las bases descartada (${c.cierreDescartado!.motivo})`);
		delete c.iaRevisar;
		delete c.iaError;
		delete c.iaIntentos;
		delete c.iaIntentadoEl;
		leidas++;
		console.log(`  ${c.codigo} leída (${model}): ${data.categoria} · ${data.resumen.slice(0, 80)}`);
	} catch (err) {
		if (err instanceof GeminiUnavailable) {
			console.log(`Gemini no disponible: ${err.message}. Lo pendiente sigue mañana.`);
			sinCupo = true;
			break;
		}
		// Si ya estaba leída (relectura), lo leído antes se queda
		c.iaIntentos = (c.iaIntentos ?? 0) + 1;
		c.iaIntentadoEl = hoy;
		c.iaError = (err as Error).message.slice(0, 300);
		fallidas++;
		console.warn(`  ${c.codigo}: ${c.iaError}`);
	}
	guardarDatos(datos); // tras cada una: si la pasada se corta, no se pierde lo leído
}
// Titulares cortos para las ya leídas: solo a partir de su resumen, sin añadir nada
const sinTitular = datos.convocatorias.filter((c) => c.ia && !c.ia.titular && !c.iaRevisar).slice(0, Number(process.env.MAX_TITULARES ?? 60));
if (sinTitular.length && !sinCupo && Date.now() < hasta) {
	try {
		const { data, model } = await generateJson<{ codigo: string; titular: string }[]>(
			{ apiKey, models, minIntervalMs: 6_000 },
			`Recibes resúmenes de convocatorias de ayudas públicas españolas. Para cada una, escribe un titular de 40 a 70 caracteres que diga qué financia, sin punto final, en español claro. Usa SOLO lo que dice el resumen y quién convoca; no añadas datos. Devuelve el mismo código de cada una.`,
			[{ text: JSON.stringify(sinTitular.map((c) => ({ codigo: c.codigo, convoca: c.organo.nivel3, resumen: c.ia!.resumen }))) }],
			{ type: 'ARRAY', items: { type: 'OBJECT', properties: { codigo: { type: 'STRING' }, titular: { type: 'STRING' } }, required: ['codigo', 'titular'] } },
		);
		const porCodigo = new Map(sinTitular.map((c) => [c.codigo, c]));
		let puestos = 0;
		for (const x of Array.isArray(data) ? data : []) {
			const c = porCodigo.get(String(x?.codigo));
			const titular = titularValido(typeof x?.titular === 'string' ? x.titular : undefined);
			if (c?.ia && titular) {
				c.ia.titular = titular;
				puestos++;
			}
		}
		console.log(`${puestos} titulares cortos para las ya leídas (${model})`);
		guardarDatos(datos);
	} catch (err) {
		// No es grave: la web usa el resumen mientras tanto
		console.log(`Titulares cortos: ${(err as Error).message.slice(0, 160)}; se intenta en la siguiente pasada`);
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
	},
};
guardarDatos(datos);
console.log(`${leidas} leídas, ${fallidas} con error`);
