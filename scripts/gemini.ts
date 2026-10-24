/**
 * Gemini (Google AI Studio), en su nivel gratuito: la clave tiene que ser de
 * un proyecto SIN facturación. Viene del radar comercial.
 *
 * El nivel gratuito tiene un cupo diario por modelo y los modelos nuevos se
 * saturan a menudo: se prueba una cadena de modelos y, si todos están
 * agotados, lo pendiente sigue al día siguiente.
 */

const BASE = 'https://generativelanguage.googleapis.com/v1beta/models';
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export interface GeminiOptions {
	apiKey: string;
	/** En orden de preferencia */
	models: string[];
	/** Pausa entre peticiones: el nivel gratuito limita las peticiones por minuto */
	minIntervalMs: number;
}

export class GeminiError extends Error {
	status: number;
	constructor(status: number, body: string) {
		super(`Gemini ${status}: ${body.slice(0, 400)}`);
		this.status = status;
	}
}

/** Ningún modelo puede responder ahora; quotaOnly: todos con el cupo del día agotado */
export class GeminiUnavailable extends Error {
	quotaOnly: boolean;
	constructor(message: string, quotaOnly: boolean) {
		super(message);
		this.quotaOnly = quotaOnly;
	}
}

let last = 0;
const exhausted = new Set<string>();

export type Part = { text: string } | { inlineData: { mimeType: string; data: string } };

/** Devuelve también el modelo que respondió */
export async function generateJson<T>(opts: GeminiOptions, system: string, parts: Part[], schema: object): Promise<{ data: T; model: string }> {
	const reasons: string[] = [];
	for (const model of opts.models) {
		if (exhausted.has(model)) continue;
		for (let attempt = 1; attempt <= 2; attempt++) {
			const wait = last + opts.minIntervalMs - Date.now();
			if (wait > 0) await sleep(wait);
			last = Date.now();

			let res: Response;
			try {
				res = await fetch(`${BASE}/${model}:generateContent`, {
					method: 'POST',
					signal: AbortSignal.timeout(180_000),
					headers: { 'content-type': 'application/json', 'x-goog-api-key': opts.apiKey },
					body: JSON.stringify({
						systemInstruction: { parts: [{ text: system }] },
						contents: [{ role: 'user', parts }],
						generationConfig: { responseMimeType: 'application/json', responseSchema: schema },
					}),
				});
			} catch (err) {
				if ((err as Error).name !== 'TimeoutError') throw err;
				reasons.push(`${model}: sin respuesta`);
				break;
			}

			if (res.ok) {
				const data = (await res.json()) as { candidates?: { content?: { parts?: { text?: string }[] } }[] };
				const text = data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? '').join('') ?? '';
				return { data: JSON.parse(text) as T, model };
			}

			const body = await res.text();
			if (res.status === 429 && /PerDay/i.test(body)) {
				exhausted.add(model);
				reasons.push(`${model}: cupo diario agotado`);
				break;
			}
			if (res.status === 429 && attempt === 1) {
				await sleep(30_000); // límite por minuto: esperar y repetir
				continue;
			}
			if (res.status === 503 || res.status === 429) {
				reasons.push(`${model}: saturado`);
				break;
			}
			throw new GeminiError(res.status, body);
		}
	}
	const quotaOnly = opts.models.every((m) => exhausted.has(m));
	throw new GeminiUnavailable(`Ningún modelo disponible (${reasons.join('; ') || 'todos agotados'})`, quotaOnly);
}
