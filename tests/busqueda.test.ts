import { describe, expect, it } from 'vitest';
import { fechaBdns, inicioBusqueda } from '../scripts/bdns';

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
