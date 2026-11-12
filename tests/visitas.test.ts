import { describe, expect, it } from 'vitest';
import { alternarGuardada, esNueva, guardadas, registrarVisita } from '../src/lib/visitas';

const memoria = () => {
	const m = new Map<string, string>();
	return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v) };
};

describe('lo que recuerda el navegador', () => {
	it('guardar y quitar ayudas', () => {
		const a = memoria();
		expect(guardadas(a).size).toBe(0);
		expect(alternarGuardada('900001', a)).toBe(true);
		expect([...guardadas(a)]).toEqual(['900001']);
		expect(alternarGuardada('900001', a)).toBe(false);
		expect(guardadas(a).size).toBe(0);
	});

	it('datos rotos o sin almacenamiento: no falla', () => {
		const a = memoria();
		a.setItem('ayudas-guardadas', '{roto');
		expect(guardadas(a).size).toBe(0);
		expect(guardadas(null).size).toBe(0);
		expect(registrarVisita('2026-11-12', null)).toBeNull();
		const lleno = { getItem: () => null, setItem: () => { throw new Error('QuotaExceeded'); } };
		expect(alternarGuardada('1', lleno)).toBe(true);
	});

	it('la visita anterior: la primera no tiene; el mismo día no cuenta como otra', () => {
		const a = memoria();
		expect(registrarVisita('2026-11-10', a)).toBeNull();
		expect(registrarVisita('2026-11-12', a)).toBe('2026-11-10');
		expect(registrarVisita('2026-11-12', a)).toBe('2026-11-10');
		expect(registrarVisita('2026-11-13', a)).toBe('2026-11-12');
	});

	it('nueva: entró en el radar (o se registró, si es antigua) después de la visita anterior', () => {
		expect(esNueva({ alta: '2026-11-12', publicada: '2026-06-01' }, '2026-11-10')).toBe(true);
		expect(esNueva({ publicada: '2026-11-09' }, '2026-11-10')).toBe(false);
		expect(esNueva({ publicada: '2026-11-11' }, null)).toBe(false);
	});
});
