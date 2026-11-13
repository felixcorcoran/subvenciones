import { describe, expect, it } from 'vitest';
import {
	alternarGuardada,
	borrarNegocio,
	cambiosGuardadas,
	esNueva,
	guardadas,
	guardadasConPlazo,
	guardarNegocio,
	negocios,
	registrarVisita,
	verCambios,
} from '../src/lib/visitas';
import { convocatoria, perfil } from './ayudas';

const memoria = () => {
	const m = new Map<string, string>();
	return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v) };
};

describe('lo que recuerda el navegador', () => {
	it('guardar y quitar ayudas, con el plazo que tenían', () => {
		const a = memoria();
		const c = convocatoria({ codigo: '900001', fin: '2026-12-01' });
		expect(guardadas(a).size).toBe(0);
		expect(alternarGuardada(c, a)).toBe(true);
		expect(guardadasConPlazo(a)).toEqual({ '900001': '2026-12-01' });
		expect(alternarGuardada(c, a)).toBe(false);
		expect(guardadas(a).size).toBe(0);
	});

	it('lee el formato antiguo (lista de códigos)', () => {
		const a = memoria();
		a.setItem('ayudas-guardadas', JSON.stringify(['1', '2']));
		expect(guardadasConPlazo(a)).toEqual({ 1: null, 2: null });
	});

	it('cambios en las guardadas: plazo distinto o ya no está; al verlos, se apunta lo de ahora', () => {
		const a = memoria();
		alternarGuardada(convocatoria({ codigo: '1', fin: '2026-12-01' }), a);
		alternarGuardada(convocatoria({ codigo: '2', fin: '2026-12-10' }), a);
		alternarGuardada(convocatoria({ codigo: '3', fin: '2026-12-20' }), a);
		const ahora = [convocatoria({ codigo: '1', fin: '2026-12-15' }), convocatoria({ codigo: '2', fin: '2026-12-10' })];
		const cambios = cambiosGuardadas(ahora, a);
		expect(cambios.map((x) => [x.codigo, x.tipo, x.antes, x.ahora])).toEqual([
			['1', 'plazo', '2026-12-01', '2026-12-15'],
			['3', 'fuera', '2026-12-20', null],
		]);
		verCambios(ahora, a);
		expect(cambiosGuardadas(ahora, a)).toEqual([]);
		expect(guardadasConPlazo(a)).toEqual({ 1: '2026-12-15', 2: '2026-12-10' });
	});

	it('datos rotos o sin almacenamiento: no falla', () => {
		const a = memoria();
		a.setItem('ayudas-guardadas', '{roto');
		expect(guardadas(a).size).toBe(0);
		expect(guardadas(null).size).toBe(0);
		expect(registrarVisita('2026-11-12', null)).toBeNull();
		const lleno = { getItem: () => null, setItem: () => { throw new Error('QuotaExceeded'); } };
		expect(alternarGuardada(convocatoria({ codigo: '1' }), lleno)).toBe(true);
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

	it('negocios guardados: el último primero, sin repetir nombre, y se pueden borrar', () => {
		const a = memoria();
		guardarNegocio({ nombre: 'Bar Pepe', perfil: perfil() }, a);
		guardarNegocio({ nombre: 'Taller Ana', perfil: perfil({ forma: 'sociedad' }), finalidad: 'digitalizar' }, a);
		guardarNegocio({ nombre: 'bar pepe', perfil: perfil({ sector: 'G' }) }, a);
		expect(negocios(a).map((n) => n.nombre)).toEqual(['bar pepe', 'Taller Ana']);
		expect(guardarNegocio({ nombre: '   ', perfil: perfil() }, a)).toHaveLength(2);
		expect(borrarNegocio('Taller Ana', a).map((n) => n.nombre)).toEqual(['bar pepe']);
		a.setItem('negocios-subvenciones', JSON.stringify([{ nombre: 'roto' }, 3]));
		expect(negocios(a)).toEqual([]);
	});
});
