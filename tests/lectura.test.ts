import { describe, expect, it } from 'vitest';
import { analisisValido, mencionaFecha, motivoCierreIaDudoso, porLeer, revisarCierreIa } from '../scripts/lectura';
import { cierre } from '../src/lib/encaje';
import type { Analisis, Convocatoria } from '../src/lib/tipos';
import { analisis, convocatoria, HOY } from './ayudas';

const guardada = (extra: Partial<Convocatoria> = {}): Convocatoria => ({
	...convocatoria(),
	textoInicio: null,
	documento: { id: 10, nombre: 'extracto.pdf' },
	ia: null,
	...extra,
});

describe('qué lee la IA', () => {
	it('las que no tienen lectura, y no las ya leídas', () => {
		expect(porLeer(guardada(), HOY)).toBe(true);
		expect(porLeer(guardada({ ia: analisis() }), HOY)).toBe(false);
		expect(porLeer(guardada({ documento: null }), HOY)).toBe(false);
	});

	it('no lee por primera vez las «por verificar» (alertas avisaría de ellas como nuevas)', () => {
		// HOY = 2026-10-25: registrada hace más de 120 días y sin fecha de cierre
		expect(porLeer(guardada({ fin: null, publicada: '2026-06-01' }), HOY)).toBe(false);
		// Con fecha de cierre por delante sí, aunque sea antigua
		expect(porLeer(guardada({ publicada: '2026-06-01' }), HOY)).toBe(true);
		// Una ya leída que cambia de documento sí se relee: alertas ya la vio
		expect(porLeer(guardada({ fin: null, publicada: '2026-06-01', ia: analisis(), iaRevisar: 'documento nuevo' }), HOY)).toBe(true);
	});

	it('las leídas que hay que releer (documento nuevo o plazo cambiado)', () => {
		expect(porLeer(guardada({ ia: analisis(), iaRevisar: 'cambió el plazo' }), HOY)).toBe(true);
	});

	it('con uno o dos fallos, se reintenta (los del modelo retirado se recuperan solos)', () => {
		expect(porLeer(guardada({ iaIntentos: 1, iaError: 'Gemini 404: …' }), HOY)).toBe(true);
		expect(porLeer(guardada({ iaIntentos: 2, iaError: 'Gemini 404: …' }), HOY)).toBe(true);
	});

	it('con tres fallos espera dos semanas, pero no se queda bloqueada para siempre', () => {
		expect(porLeer(guardada({ iaIntentos: 3, iaIntentadoEl: '2026-10-20' }), HOY)).toBe(false);
		expect(porLeer(guardada({ iaIntentos: 3, iaIntentadoEl: '2026-10-11' }), HOY)).toBe(true);
		// Datos de antes, sin fecha del último intento: se prueba ya
		expect(porLeer(guardada({ iaIntentos: 3 }), HOY)).toBe(true);
	});
});

describe('fecha de cierre sacada de las bases', () => {
	/** Sin fecha en la BDNS, con la fecha de las bases bien explicada */
	const deBases = (extra: Partial<Convocatoria> = {}, ia: Partial<Analisis> = {}) =>
		guardada({ fin: null, ia: analisis({ plazoFin: '2026-11-02', plazo: 'Desde la publicación hasta el 2 de noviembre de 2026.', ...ia }), ...extra });

	it('nombra la fecha en el texto del plazo', () => {
		expect(mencionaFecha('Hasta el 2 de noviembre de 2026.', '2026-11-02')).toBe(true);
		expect(mencionaFecha('Del 15 al 31 de enero de 2027', '2027-01-31')).toBe(true);
		expect(mencionaFecha('Hasta el 02/11/2026', '2026-11-02')).toBe(true);
		expect(mencionaFecha('Hasta el 12 de noviembre', '2026-11-02')).toBe(false);
		expect(mencionaFecha('Un mes desde la publicación', '2026-11-02')).toBe(false);
	});

	it('sin fecha en la BDNS, una fecha clara de las bases vale', () => {
		const c = deBases();
		expect(motivoCierreIaDudoso(c)).toBeNull();
		expect(revisarCierreIa(c)).toBe(false);
		expect(c.ia?.plazoFin).toBe('2026-11-02');
	});

	it('si contradice a la BDNS, manda la BDNS y la de la IA se guarda aparte', () => {
		const c = deBases({ fin: '2026-12-09' }, { plazoFin: '2026-10-30', plazo: 'Hasta el 30 de octubre de 2026.' });
		expect(revisarCierreIa(c)).toBe(true);
		expect(c.ia?.plazoFin).toBeNull();
		expect(cierre(c)).toBe('2026-12-09');
		expect(c.cierreDescartado).toEqual({ fecha: '2026-10-30', motivo: 'no coincide con la de la BDNS (2026-12-09)' });
	});

	it('si coincide con la BDNS, se queda aunque las bases no la nombren o tengan dudas', () => {
		const c = deBases({ fin: '2026-11-02' }, { plazo: '15 días naturales desde la publicación en el BOP.', dudas: 'Hay fechas de fin de plazo contradictorias.' });
		expect(revisarCierreIa(c)).toBe(false);
		expect(cierre(c)).toBe('2026-11-02');
	});

	it('una fecha dudosa no se usa (y no puede cerrar la convocatoria)', () => {
		// El texto del plazo no la nombra: la fecha se ha calculado o supuesto
		const calculada = deBases({}, { plazo: 'Un mes desde la publicación del extracto en el boletín.' });
		expect(revisarCierreIa(calculada)).toBe(true);
		expect(cierre(calculada)).toBeNull();
		// Anterior al registro en la BDNS
		expect(motivoCierreIaDudoso(deBases({ publicada: '2026-11-10' }))).toBe('es anterior al registro en la BDNS');
		// Anterior a la apertura del plazo según la BDNS, sin que el texto la nombre: no es un plazo previo
		expect(motivoCierreIaDudoso(deBases({ inicio: '2026-11-05' }, { plazo: 'Diez días desde la publicación en el BOP.' }))).toBe(
			'es anterior a la apertura del plazo en la BDNS (2026-11-05)',
		);
		// La propia lectura duda del plazo
		expect(motivoCierreIaDudoso(deBases({}, { dudas: 'No queda claro si el plazo se amplía.' }))).toBe('la lectura de las bases tiene dudas sobre el plazo');
	});

	it('es idempotente y no toca las que no tienen fecha de la IA', () => {
		const c = deBases({ fin: '2026-12-09' });
		revisarCierreIa(c);
		const despues = structuredClone(c);
		expect(revisarCierreIa(c)).toBe(false);
		expect(c).toEqual(despues);
		expect(revisarCierreIa(guardada({ ia: analisis({ plazoFin: null }) }))).toBe(false);
		expect(revisarCierreIa(guardada())).toBe(false);
	});
});

describe('respuesta de la IA', () => {
	it('una respuesta completa vale', () => {
		expect(analisisValido(analisis())).toBe(true);
	});

	it('una respuesta a medias o con valores raros no se guarda', () => {
		const { resumen, ...sinResumen } = analisis();
		expect(analisisValido(sinResumen)).toBe(false);
		expect(analisisValido(analisis({ resumen: '  ' }))).toBe(false);
		expect(analisisValido(analisis({ categoria: 'subvencion' as never }))).toBe(false);
		expect(analisisValido(analisis({ requisitos: 'uno' as never }))).toBe(false);
		expect(analisisValido(analisis({}, { autonomos: 'quizá' as never }))).toBe(false);
		expect(analisisValido({ ...analisis(), condiciones: undefined })).toBe(false);
		expect(analisisValido(null)).toBe(false);
	});
});
