import { describe, expect, it } from 'vitest';
import { fechaBdns, inicioBusqueda, tramoRelleno } from '../scripts/bdns';

describe('inicioBusqueda', () => {
	it('cubre una convocatoria que aparece una semana después de registrarse', () => {
		// Caso real: 938128 se registró el 27/10 y salió en la búsqueda el 3/11;
		// la pasada del 3/11 por la mañana tenía la anterior del 2/11 por la noche
		const desde = inicioBusqueda('2026-11-02T22:55:00.000Z', new Date('2026-11-03T13:00:00Z'), 14);
		expect(desde <= new Date('2026-10-27T00:00:00Z')).toBe(true);
	});

	it('no va más de 30 días atrás aunque la última pasada sea antigua', () => {
		const ahora = new Date('2026-11-09T12:00:00Z');
		expect(fechaBdns(inicioBusqueda('2026-08-01T12:00:00Z', ahora, 14))).toBe('10/10/2026');
	});

	it('la primera vez busca DIAS_ATRAS días', () => {
		expect(fechaBdns(inicioBusqueda(undefined, new Date('2026-11-09T12:00:00Z'), 14))).toBe('26/10/2026');
	});
});

describe('tramoRelleno', () => {
	it('la primera vez empieza justo antes de la convocatoria más antigua', () => {
		const t = tramoRelleno(undefined, '2026-11-12', '2026-10-20', 180, 10)!;
		expect(t).toEqual({ desde: '2026-10-10', hasta: '2026-10-19', siguiente: { desde: '2026-10-10', limite: '2026-05-16' } });
	});

	it('cada pasada sigue donde lo dejó la anterior y no pasa del límite', () => {
		const t = tramoRelleno({ desde: '2026-05-20', limite: '2026-05-16' }, '2026-11-20', '2026-10-20', 180, 10)!;
		expect(t.desde).toBe('2026-05-16');
		expect(t.hasta).toBe('2026-05-19');
		expect(tramoRelleno(t.siguiente, '2026-11-21', '2026-10-20', 180, 10)).toBeNull();
	});
});
