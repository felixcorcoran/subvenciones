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
 */
import { documento } from './bdns.ts';
import { GeminiUnavailable, generateJson } from './gemini.ts';
import { guardarDatos, leerDatos } from './almacen.ts';
import type { Analisis, Convocatoria } from '../src/lib/tipos.ts';

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
	console.log('Sin GEMINI_API_KEY: no se leen bases en esta pasada');
	process.exit(0);
}
const models = (process.env.GEMINI_MODELS ?? 'gemini-3.8-flash,gemini-3.6-flash,gemini-3.7-flash,gemini-3.5-flash,gemini-2.5-flash,gemini-3.5-flash-lite,gemini-2.5-flash-lite').split(',').map((m) => m.trim());
const max = Number(process.env.MAX_ANALIZAR ?? 40);
const MAX_BYTES = 18 * 1024 * 1024;

const datos = leerDatos();
const pendientes = datos.convocatorias.filter((c) => !c.ia && c.documento && (c.iaIntentos ?? 0) < 3).slice(0, max);
console.log(`${pendientes.length} convocatorias por leer`);

const SISTEMA = `Lees convocatorias de ayudas públicas españolas y las explicas al dueño de un pequeño negocio o a un autónomo, que no sabe de jerga administrativa.

Reglas:
- Usa SOLO lo que dice el documento. Si un dato no aparece, pon "no_consta" o null. No supongas ni completes con lo habitual.
- Español claro y directo, frases cortas, sin jerga («beneficiario», «ex post», «acreditar» → dilo con palabras normales).
- categoria: "ayuda" si es dinero (o una ventaja) que un negocio puede pedir para su actividad; "premio" si es un concurso o premio; "otro" si es otra cosa (seleccionar una entidad colaboradora, becas para personas, etc.).
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
	required: ['categoria', 'resumen', 'paraQuien', 'importe', 'importeMaximo', 'plazo', 'plazoFin', 'requisitos', 'gastos', 'condiciones', 'dudas'],
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
for (const c of pendientes) {
	const doc = c.documento!;
	try {
		if (!/\.pdf$/i.test(doc.nombre)) throw new Error(`no es un PDF (${doc.nombre})`);
		const bytes = await documento(doc.id);
		if (bytes.length > MAX_BYTES) throw new Error(`documento demasiado grande (${Math.round(bytes.length / 1e6)} MB)`);
		const { data, model } = await generateJson<Analisis>(
			{ apiKey, models, minIntervalMs: 6_000 },
			SISTEMA,
			[{ inlineData: { mimeType: 'application/pdf', data: bytes.toString('base64') } }, { text: contexto(c) }],
			ESQUEMA,
		);
		data.plazoFin = valida(data.plazoFin);
		c.ia = data;
		c.iaModelo = model;
		delete c.iaError;
		leidas++;
		console.log(`  ${c.codigo} leída (${model}): ${data.categoria} · ${data.resumen.slice(0, 80)}`);
	} catch (err) {
		if (err instanceof GeminiUnavailable) {
			console.log(`Gemini no disponible: ${err.message}. Lo pendiente sigue mañana.`);
			break;
		}
		c.iaIntentos = (c.iaIntentos ?? 0) + 1;
		c.iaError = (err as Error).message.slice(0, 300);
		console.warn(`  ${c.codigo}: ${c.iaError}`);
	}
	guardarDatos(datos); // tras cada una: si la pasada se corta, no se pierde lo leído
}
console.log(`${leidas} leídas`);
