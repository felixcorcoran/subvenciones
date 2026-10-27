import { describe, expect, it } from 'vitest';
import { enLista, estado, nombresCambios, origenCierre, plazoSinFecha, sinComprobarDesde, textoPlazo } from '../src/lib/estado';
import { analisis, convocatoria, HOY } from './ayudas';

describe('estado en la web', () => {
	it('sin fecha de cierre: el plazo con palabras, o nada si no se sabe', () => {
		expect(plazoSinFecha(convocatoria({ fin: null }))).toBe('Hasta el 31 de diciembre de 2026.');
		expect(plazoSinFecha(convocatoria({ fin: null, ia: null, textoFin: 'Un mes desde la publicación' }))).toBe('Un mes desde la publicación');
		expect(plazoSinFecha(convocatoria({ fin: null, ia: null }))).toBeNull();
	});

	it('si el plazo cambió y la IA no lo ha releído, no se enseña su texto viejo', () => {
		expect(plazoSinFecha(convocatoria({ fin: null, iaRevisar: 'cambió el plazo', textoFin: null }))).toBeNull();
		expect(plazoSinFecha(convocatoria({ fin: null, iaRevisar: 'cambió el plazo', ia: analisis(), textoFin: 'Hasta fin de fondos' }))).toBe('Hasta fin de fondos');
	});

	it('avisa si su ficha lleva más de tres días sin comprobarse', () => {
		expect(sinComprobarDesde(convocatoria({ comprobada: HOY }), HOY)).toBeNull();
		expect(sinComprobarDesde(convocatoria({ comprobada: '2026-10-22' }), HOY)).toBeNull();
		expect(sinComprobarDesde(convocatoria({ comprobada: '2026-10-21' }), HOY)).toBe('2026-10-21');
		// Datos de antes de las comprobaciones: no se sabe, no se afirma nada
		expect(sinComprobarDesde(convocatoria(), HOY)).toBeNull();
	});

	it('estado de cada convocatoria', () => {
		// HOY = 2026-10-25; la de prueba se registró el 2026-10-01 y cierra el 2026-12-31 (BDNS)
		expect(estado(convocatoria(), HOY)).toBe('abierta');
		expect(estado(convocatoria({ fin: '2026-10-24' }), HOY)).toBe('cerrada');
		expect(estado(convocatoria({ inicio: '2026-11-01' }), HOY)).toBe('proxima');
		expect(estado(convocatoria({ fin: null }), HOY)).toBe('sin_fecha');
		// Sin fecha de cierre y registrada hace más de 120 días: por verificar, nunca «cerrada»
		expect(estado(convocatoria({ fin: null, publicada: '2026-06-26' }), HOY)).toBe('por_verificar');
		expect(estado(convocatoria({ fin: null, publicada: '2026-06-27' }), HOY)).toBe('sin_fecha');
		// Antigua pero con fecha de cierre por delante: abierta
		expect(estado(convocatoria({ publicada: '2026-01-01' }), HOY)).toBe('abierta');
	});

	it('solo salen en la lista las abiertas, las próximas y las sin fecha', () => {
		expect(['abierta', 'proxima', 'sin_fecha', 'por_verificar', 'cerrada'].filter((e) => enLista(e as never))).toEqual(['abierta', 'proxima', 'sin_fecha']);
	});

	it('el plazo dice de dónde sale la fecha', () => {
		expect(origenCierre(convocatoria())).toBe('bdns');
		expect(textoPlazo(convocatoria(), HOY)).toBe('Hasta el 31 de diciembre de 2026');
		const deBases = convocatoria({ fin: null, ia: analisis({ plazoFin: '2026-11-02' }) });
		expect(origenCierre(deBases)).toBe('bases');
		expect(textoPlazo(deBases, HOY)).toBe('Hasta el 2 de noviembre de 2026 (según las bases)');
		expect(textoPlazo(convocatoria({ inicio: '2026-11-01' }), HOY)).toBe('Abre el 1 de noviembre de 2026 · hasta el 31 de diciembre de 2026');
		expect(textoPlazo(convocatoria({ fin: null, ia: null }), HOY)).toBe('Sin fecha de cierre confirmada');
	});

	it('los cambios, con nombres que se entienden', () => {
		expect(nombresCambios(['fin', 'documentos'])).toBe('fecha de cierre, documentos');
	});
});
