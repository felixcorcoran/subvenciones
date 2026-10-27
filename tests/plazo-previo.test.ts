/**
 * Plazo previo al periodo de la BDNS: campañas de bonos en las que el comercio
 * se adhiere antes de las fechas de la ficha de la BDNS (que son las de los
 * bonos para los vecinos). Casos reales: BDNS 937103 (Benilloba) y 937020
 * (Penàguila), con sus fechas de la BDNS y el plazo leído en sus bases.
 */
import { describe, expect, it } from 'vitest';
import { motivoCierreIaDudoso, revisarCierreIa } from '../scripts/lectura';
import { eventoIcs } from '../src/lib/calendario';
import { cierre, encaje } from '../src/lib/encaje';
import { enLista, estado, origenCierre, textoPlazo } from '../src/lib/estado';
import { plazoPrevio } from '../src/lib/fechas';
import type { Convocatoria } from '../src/lib/tipos';
import { analisis, convocatoria, perfil } from './ayudas';

const bono = (codigo: string, extra: Partial<Convocatoria>, plazo: string): Convocatoria => ({
	...convocatoria({ codigo, regiones: [{ codigo: 'ES521', nombre: 'Alicante / Alacant' }], inicio: '2026-11-02', ia: analisis({ plazoFin: '2026-10-30', plazo }) }),
	textoInicio: null,
	documento: { id: 1, nombre: 'bases.pdf' },
	...extra,
});

const BENILLOBA = bono(
	'937103',
	{ publicada: '2026-10-22', fin: '2026-12-09' },
	'Adhesión de comercios abierta hasta las 12:00 horas del 30 de octubre de 2026. Los bonos se canjean y justifican hasta las 14:00 horas del 9 de diciembre de 2026.',
);
const PENAGUILA = bono('937020', { publicada: '2026-10-21', fin: '2026-12-10' }, 'Para que el negocio se adhiera: del 26 al 30 de octubre de 2026.');

const ANTES = '2026-10-27';
const ULTIMO_DIA = '2026-10-30';
const DESPUES = '2026-10-31';
/** Un comercio de Alicante, para que solo el plazo decida */
const COMERCIO = perfil({ provincia: 'ES521', sector: 'G' });
const motivoPlazo = (c: Convocatoria, hoy: string) => encaje(c, COMERCIO, hoy).motivos.find((m) => /plazo/i.test(m.texto))?.texto;

describe.each([
	['937103 Benilloba', BENILLOBA],
	['937020 Penàguila', PENAGUILA],
])('plazo previo: %s', (_, original) => {
	it('la fecha de las bases se queda como cierre para el negocio; las de la BDNS no cambian', () => {
		const c = structuredClone(original);
		expect(plazoPrevio(c)).toBe(true);
		expect(motivoCierreIaDudoso(c)).toBeNull();
		expect(revisarCierreIa(c)).toBe(false);
		expect(c.cierreDescartado).toBeUndefined();
		expect(cierre(c)).toBe('2026-10-30');
		expect(origenCierre(c)).toBe('bases');
		expect([c.inicio, c.fin]).toEqual([original.inicio, original.fin]);
	});

	it('antes del 30 de octubre: abierta, «hasta el 30 de octubre (según las bases)», sin «Abre el 2 de noviembre»', () => {
		for (const hoy of [ANTES, ULTIMO_DIA]) {
			expect(estado(original, hoy)).toBe('abierta');
			expect(enLista(estado(original, hoy))).toBe(true);
			expect(textoPlazo(original, hoy)).toBe('Hasta el 30 de octubre de 2026 (según las bases)');
			expect(textoPlazo(original, hoy)).not.toContain('Abre');
			// El encaje no lo da por cerrado
			expect(motivoPlazo(original, hoy)).toBeUndefined();
		}
	});

	it('después del 30 de octubre: fuera de la lista y sin encajar, pero no cerrada mientras siga el periodo de la BDNS', () => {
		expect(estado(original, DESPUES)).toBe('plazo_previo_cerrado');
		expect(enLista(estado(original, DESPUES))).toBe(false);
		expect(textoPlazo(original, DESPUES)).toBe('Plazo terminado el 30 de octubre de 2026 (según las bases)');
		// Tarjeta, ficha y encaje dicen lo mismo: el plazo del negocio terminó el 30
		expect(motivoPlazo(original, DESPUES)).toBe('El plazo terminó el 30 de octubre de 2026');
		expect(encaje(original, COMERCIO, DESPUES).estado).toBe('no');
	});

	it('cerrada (y se puede retirar) solo cuando pasa también el fin de la BDNS', () => {
		expect(estado(original, original.fin!)).toBe('plazo_previo_cerrado');
		const siguiente = new Date(`${original.fin}T00:00:00Z`);
		siguiente.setUTCDate(siguiente.getUTCDate() + 1);
		expect(estado(original, siguiente.toISOString().slice(0, 10))).toBe('cerrada');
	});

	it('el calendario apunta el cierre de las bases', () => {
		expect(eventoIcs(original, 'https://ejemplo.es/ficha')).toContain('DTSTART;VALUE=DATE:20261030');
	});
});

describe('plazo previo: solo con las tres condiciones', () => {
	it('si el texto del plazo no nombra la fecha, manda la BDNS (comportamiento prudente)', () => {
		const c = structuredClone(PENAGUILA);
		c.ia!.plazo = 'Diez días hábiles desde la publicación en el BOP.';
		expect(plazoPrevio(c)).toBe(false);
		expect(revisarCierreIa(c)).toBe(true);
		expect(c.cierreDescartado?.motivo).toBe('no coincide con la de la BDNS (2026-12-10)');
		expect(cierre(c)).toBe('2026-12-10');
		expect(textoPlazo(c, ANTES)).toBe('Abre el 2 de noviembre de 2026 · hasta el 10 de diciembre de 2026');
	});

	it('si la fecha es anterior al registro, no vale', () => {
		const c = structuredClone(PENAGUILA);
		c.publicada = '2026-10-31';
		expect(plazoPrevio(c)).toBe(false);
		expect(revisarCierreIa(c)).toBe(true);
		expect(cierre(c)).toBe('2026-12-10');
	});

	it('una ayuda normal: la fecha de la BDNS sigue mandando sobre una de las bases posterior al inicio', () => {
		const c = structuredClone(BENILLOBA);
		c.ia = analisis({ plazoFin: '2026-11-20', plazo: 'Hasta el 20 de noviembre de 2026.' });
		expect(plazoPrevio(c)).toBe(false);
		expect(revisarCierreIa(c)).toBe(true);
		expect(cierre(c)).toBe('2026-12-09');
		expect(origenCierre(c)).toBe('bdns');
		// Y una normal sin fecha de las bases se comporta como siempre
		const normal = convocatoria({ inicio: '2026-11-02' });
		expect(plazoPrevio(normal)).toBe(false);
		expect(estado(normal, ANTES)).toBe('proxima');
		expect(textoPlazo(normal, ANTES)).toBe('Abre el 2 de noviembre de 2026 · hasta el 31 de diciembre de 2026');
	});
});
