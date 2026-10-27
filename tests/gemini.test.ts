import { afterEach, describe, expect, it, vi } from 'vitest';
import { GeminiUnavailable, generateJson } from '../scripts/gemini';

const opciones = (models: string[]) => ({ apiKey: 'prueba', models, minIntervalMs: 0 });
const respuesta = (status: number, cuerpo: unknown) =>
	new Response(typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo), { status });
const correcta = (data: unknown) => respuesta(200, { candidates: [{ content: { parts: [{ text: JSON.stringify(data) }] } }] });

afterEach(() => vi.unstubAllGlobals());

describe('cadena de modelos de Gemini', () => {
	it('salta un modelo retirado (404) y responde con el siguiente', async () => {
		const fetch = vi.fn().mockResolvedValueOnce(respuesta(404, 'model is no longer available')).mockResolvedValueOnce(correcta({ ok: true }));
		vi.stubGlobal('fetch', fetch);
		const { data, model } = await generateJson(opciones(['retirado-a', 'vigente-a']), 'sistema', [{ text: 'hola' }], {});
		expect(data).toEqual({ ok: true });
		expect(model).toBe('vigente-a');
	});

	it('si solo quedan modelos retirados, lo pendiente sigue otro día (no es fallo de la convocatoria)', async () => {
		vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => respuesta(404, 'not found')));
		await expect(generateJson(opciones(['retirado-b', 'retirado-c']), 'sistema', [{ text: 'hola' }], {})).rejects.toBeInstanceOf(GeminiUnavailable);
	});

	it('un fallo del servidor (500) o de red pasa al siguiente modelo', async () => {
		const fetch = vi
			.fn()
			.mockResolvedValueOnce(respuesta(500, 'internal'))
			.mockRejectedValueOnce(new TypeError('fetch failed'))
			.mockResolvedValueOnce(correcta({ ok: 1 }));
		vi.stubGlobal('fetch', fetch);
		const { model } = await generateJson(opciones(['caido-a', 'caido-b', 'vigente-c']), 'sistema', [{ text: 'hola' }], {});
		expect(model).toBe('vigente-c');
	});

	it('un error de la petición (400) sí se atribuye a la convocatoria', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuesta(400, 'documento no válido')));
		await expect(generateJson(opciones(['vigente-b']), 'sistema', [{ text: 'hola' }], {})).rejects.not.toBeInstanceOf(GeminiUnavailable);
	});
});
