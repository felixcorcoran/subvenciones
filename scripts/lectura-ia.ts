/**
 * La lectura de las bases con Gemini: las instrucciones, el esquema de la
 * respuesta y `crearLector`, que lee el PDF de una convocatoria y devuelve lo
 * leído ya validado y en su forma. La usan la pasada diaria (analizar.ts) y la
 * evaluación de modelos (evaluar.ts), para que se evalúe lo mismo que se usa.
 */
import { documento } from './bdns.ts';
import { generateJson } from './gemini.ts';
import { analisisValido, normalizarLectura, titularValido } from './lectura.ts';
import { CLAVES_FINALIDAD } from '../src/lib/finalidades.ts';
import type { Analisis, Convocatoria } from '../src/lib/tipos.ts';

const MAX_BYTES = 18 * 1024 * 1024;

export const SISTEMA = `Lees convocatorias de ayudas públicas españolas y las explicas al dueño de un pequeño negocio o a un autónomo, que no sabe de jerga administrativa.

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
- condiciones.municipio: si el negocio tiene que estar en uno o varios municipios concretos, sus nombres separados por comas. Si la condición es estar en una comunidad autónoma o una provincia entera, null (esa zona ya se sabe por otra vía). Si no hay condición de municipio, null.
- dudas: una frase corta con lo importante que no queda claro, o null.
- finalidades: para qué sirve la ayuda, de esta lista (una o varias; vacía si ninguna encaja): ${CLAVES_FINALIDAD.join(', ')}. empezar = darse de alta o montar un negocio; contratar = contratar o mantener empleo; digitalizar = web, software, comercio electrónico; invertir = equipos, maquinaria, vehículos u obras; local = alquiler, compra o reforma del local; energia = eficiencia energética, renovables, sostenibilidad; innovar = innovación e I+D; exportar = ferias, internacionalización, promoción; formacion = formación; liquidez = gastos corrientes, cuotas, pérdidas o liquidez.
- comoPedir: los pasos para pedirla, como mucho 6, cada uno en una frase corta (dónde se presenta, si hay que registrarse o tener certificado digital, qué se hace después). Lista vacía si no lo dice.
- documentacion: los documentos que hay que presentar con la solicitud, como mucho 12, cada uno breve. Lista vacía si no lo dice.
- dondeSolicitar: dónde se presenta la solicitud, en una frase («En la sede electrónica del Ayuntamiento de Cabra»), o null.
- enlaceSolicitud: la dirección web para pedirla, solo si aparece escrita en el documento, copiada tal cual; si no aparece, null.`;

const SINO = { type: 'STRING', enum: ['si', 'no', 'no_consta'] };
export const ESQUEMA = {
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
		finalidades: { type: 'ARRAY', items: { type: 'STRING', enum: CLAVES_FINALIDAD } },
		comoPedir: { type: 'ARRAY', items: { type: 'STRING' } },
		documentacion: { type: 'ARRAY', items: { type: 'STRING' } },
		dondeSolicitar: { type: 'STRING', nullable: true },
		enlaceSolicitud: { type: 'STRING', nullable: true },
	},
	required: ['categoria', 'resumen', 'titular', 'paraQuien', 'importe', 'importeMaximo', 'plazo', 'plazoFin', 'requisitos', 'gastos', 'condiciones', 'dudas', 'finalidades', 'comoPedir', 'documentacion', 'dondeSolicitar', 'enlaceSolicitud'],
};

export function contexto(c: Convocatoria): string {
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

/** Si la dirección responde (sin seguir a ciegas lo que diga la IA): si no, no se enseña */
async function responde(url: string): Promise<boolean> {
	for (const method of ['HEAD', 'GET']) {
		try {
			const res = await fetch(url, { method, redirect: 'follow', signal: AbortSignal.timeout(10_000) });
			if (res.ok) return true;
			if (method === 'HEAD' && (res.status === 405 || res.status === 501)) continue;
			return false;
		} catch {
			return false;
		}
	}
	return false;
}

export type Lector = (c: Convocatoria) => Promise<{ data: Analisis; model: string } | 'sin documento'>;

/**
 * Lee las bases de una convocatoria con la cadena de modelos: la lectura ya
 * validada y en su forma, 'sin documento' si la BDNS no lo da, o un error
 * (GeminiUnavailable si no queda modelo con cupo)
 */
export const crearLector =
	(apiKey: string, models: string[]): Lector =>
	async (c) => {
	const doc = c.documento!;
	let bytes: Buffer | null = null;
	if (/\.pdf$/i.test(doc.nombre)) {
		try {
			bytes = await documento(doc.id);
		} catch (err) {
			// La BDNS no ha dado el documento: es un fallo de consulta, no cuenta como intento
			console.warn(`  ${c.codigo}: sin documento (${(err as Error).message.slice(0, 120)}); se reintenta en la próxima pasada`);
			return 'sin documento';
		}
	}
	if (!bytes) throw new Error(`no es un PDF (${doc.nombre})`);
	if (bytes.length > MAX_BYTES) throw new Error(`documento demasiado grande (${Math.round(bytes.length / 1e6)} MB)`);
	const { data: bruto, model } = await generateJson<Analisis>(
		{ apiKey, models, minIntervalMs: 6_000 },
		SISTEMA,
		[{ inlineData: { mimeType: 'application/pdf', data: bytes.toString('base64') } }, { text: contexto(c) }],
		ESQUEMA,
	);
	// Una respuesta a medias no sustituye a nada: cuenta como intento fallido
	if (!analisisValido(bruto)) throw new Error(`respuesta incompleta de ${model}`);
	const data = normalizarLectura(bruto);
	data.plazoFin = valida(data.plazoFin);
	const titular = titularValido(data.titular);
	if (titular) data.titular = titular;
	else delete data.titular;
	if (data.enlaceSolicitud && !(await responde(data.enlaceSolicitud))) {
		console.log(`  ${c.codigo}: el enlace para pedirla no responde; no se enseña`);
		data.enlaceSolicitud = null;
	}
	return { data, model };
};
