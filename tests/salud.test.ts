import { describe, expect, it } from 'vitest';
import { diagnostico } from '../scripts/salud';
import type { Datos, Salud } from '../src/lib/tipos';

const AHORA = new Date('2026-10-25T10:00:00Z');

/** Datos de una recogida de esta mañana que ha ido bien */
function datos(actualizado = '2026-10-25T05:40:00Z', salud: Salud = {}): Datos {
	return {
		actualizado,
		convocatorias: [],
		salud: {
			recogida: { fecha: actualizado, nuevasSinFicha: 0, fichasComprobadas: 140, fichasFallidas: 0 },
			lectura: { fecha: actualizado, pendientes: 10, leidas: 5, fallidas: 0, ultimaLectura: '2026-10-25' },
			...salud,
		},
	};
}

describe('vigilancia de la recogida', () => {
	it('una pasada correcta, aunque no haya nada nuevo, no es un problema', () => {
		expect(diagnostico(datos(), AHORA)).toEqual([]);
		const sinNada = datos('2026-10-25T05:40:00Z', {
			lectura: { fecha: '2026-10-25T05:50:00Z', pendientes: 0, leidas: 0, fallidas: 0, ultimaLectura: '2026-10-01' },
		});
		expect(diagnostico(sinNada, AHORA)).toEqual([]);
	});

	it('fallo de la ejecución: más de 30 horas sin una recogida completa', () => {
		// Falla la de esta mañana, pero la de ayer por la tarde fue bien: aún no se avisa
		expect(diagnostico(datos('2026-10-24T14:40:00Z'), AHORA)).toEqual([]);
		const p = diagnostico(datos('2026-10-24T03:00:00Z'), AHORA);
		expect(p.map((x) => x.tipo)).toEqual(['ejecucion']);
		expect(p[0].texto).toContain('2026-10-24');
		expect(diagnostico({ actualizado: '', convocatorias: [] }, AHORA).map((x) => x.tipo)).toEqual(['ejecucion']);
	});

	it('fallo parcial de la BDNS: muchas fichas sin devolver', () => {
		const mal = datos(undefined, { recogida: { fecha: '2026-10-25T05:40:00Z', nuevasSinFicha: 0, fichasComprobadas: 20, fichasFallidas: 120 } });
		expect(diagnostico(mal, AHORA).map((x) => x.tipo)).toEqual(['bdns']);
		// Unas pocas que fallan no son un aviso
		const pocas = datos(undefined, { recogida: { fecha: '2026-10-25T05:40:00Z', nuevasSinFicha: 2, fichasComprobadas: 136, fichasFallidas: 4 } });
		expect(diagnostico(pocas, AHORA)).toEqual([]);
	});

	it('fallo parcial de la IA: días sin leer con bases pendientes', () => {
		const lectura = { fecha: '2026-10-25T05:50:00Z', pendientes: 30, leidas: 0, fallidas: 0 };
		expect(diagnostico(datos(undefined, { lectura: { ...lectura, ultimaLectura: '2026-10-23' } }), AHORA)).toEqual([]);
		expect(diagnostico(datos(undefined, { lectura: { ...lectura, ultimaLectura: '2026-10-21' } }), AHORA).map((x) => x.tipo)).toEqual(['ia']);
		// Sin pendientes no hay nada que leer: no es un fallo
		expect(diagnostico(datos(undefined, { lectura: { ...lectura, pendientes: 0, ultimaLectura: '2026-10-01' } }), AHORA)).toEqual([]);
	});

	it('datos de antes de la vigilancia (sin salud): solo se mira la fecha de la recogida', () => {
		expect(diagnostico({ actualizado: '2026-10-25T05:40:00Z', convocatorias: [] }, AHORA)).toEqual([]);
	});
});
