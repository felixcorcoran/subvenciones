import { describe, expect, it } from 'vitest';
import { comparar, informe, verdadBdns } from '../scripts/evaluacion';
import type { Convocatoria } from '../src/lib/tipos';
import { analisis, convocatoria } from './ayudas';

const conv = (extra: Partial<Convocatoria> = {}) => ({ ...convocatoria(extra), documento: null, ...extra }) as Convocatoria;
const ayto = { nivel1: 'LOCAL', nivel2: 'ELCHE/ELX', nivel3: 'AYUNTAMIENTO DE ELCHE/ELX' };

describe('evaluación de la lectura con IA', () => {
	it('la verdad de la BDNS: fecha de su ficha y municipio del ayuntamiento que convoca', () => {
		expect(verdadBdns(conv({ fin: '2026-12-01' }))).toEqual({ plazoFin: '2026-12-01' });
		expect(verdadBdns(conv({ fin: null, organo: ayto }))).toEqual({ municipio: 'Elche/elx' });
	});

	it('acierto, «no consta» o error; los nombres bilingües valen en cualquiera de las dos lenguas', () => {
		const c = conv({ fin: '2026-12-01', organo: ayto });
		const r = (ia: ReturnType<typeof analisis>) => Object.fromEntries(comparar(c, ia).map((f) => [f.campo, f.resultado]));
		expect(r(analisis({ plazoFin: '2026-12-01' }, { municipio: 'Elx' }))).toEqual({ plazoFin: 'acierto', municipio: 'acierto' });
		expect(r(analisis({ plazoFin: null }, { municipio: null }))).toEqual({ plazoFin: 'no_consta', municipio: 'no_consta' });
		expect(r(analisis({ plazoFin: '2026-11-15' }, { municipio: 'Alicante' }))).toEqual({ plazoFin: 'error', municipio: 'error' });
	});

	it('un posible plazo previo (antes del inicio de la BDNS) no se juzga contra la BDNS', () => {
		const c = conv({ fin: '2026-12-31', inicio: '2026-12-01' });
		expect(comparar(c, analisis({ plazoFin: '2026-11-20' }))).toEqual([]);
	});

	it('lo anotado a mano manda sobre la BDNS y cubre lo demás', () => {
		const c = conv({ fin: '2027-12-31' });
		const filas = comparar(c, analisis({ plazoFin: '2027-01-29', finalidades: ['digitalizar', 'contratar'] }, { autonomos: 'si' }), {
			codigo: c.codigo,
			plazoFin: '2027-01-29',
			autonomos: 'no',
			finalidades: ['digitalizar'],
		});
		expect(filas.map((f) => [f.campo, f.fuente, f.resultado])).toEqual([
			['plazoFin', 'anotado', 'acierto'],
			['autonomos', 'anotado', 'error'],
			['finalidades', 'anotado', 'acierto'],
		]);
	});

	it('el informe resume por campo y lista los errores', () => {
		const c = conv({ fin: '2026-12-01' });
		const texto = informe('Prueba', [...comparar(c, analisis({ plazoFin: '2026-12-01' })), ...comparar(c, analisis({ plazoFin: '2026-11-01' }))]);
		expect(texto).toContain('| plazoFin (BDNS) | 2 | 1 | 0 | 1 | 50 % |');
		expect(texto).toContain('esperado "2026-12-01", la IA dio "2026-11-01"');
	});
});
