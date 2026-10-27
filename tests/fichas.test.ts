import { describe, expect, it } from 'vitest';
import type { Ficha } from '../scripts/bdns';
import { aConvocatoria, actualizar, fichaValida } from '../scripts/fichas';
import { revisarCierreIa } from '../scripts/lectura';
import { analisis } from './ayudas';

const AYER = '2026-10-24';
const HOY = '2026-10-25';

/** La ficha de la BDNS de una convocatoria ficticia; cada test cambia solo lo que prueba */
function fichaBdns(extra: Partial<Ficha> = {}): Ficha {
	return {
		id: 1,
		codigoBDNS: '900001',
		organo: { nivel1: 'AUTONOMICA', nivel2: 'ANDALUCÍA', nivel3: 'CONSEJERÍA DE EMPLEO' },
		sedeElectronica: 'https://sede.ejemplo.es',
		fechaRecepcion: '2026-10-01',
		tipoConvocatoria: 'Concurrencia competitiva - canónica',
		presupuestoTotal: 100000,
		descripcion: 'Ayudas a la digitalización del comercio',
		tiposBeneficiarios: [{ descripcion: 'PYME Y PERSONAS FÍSICAS QUE DESARROLLAN ACTIVIDAD ECONÓMICA' }],
		sectores: [],
		regiones: [{ descripcion: 'ES61 - ANDALUCIA' }],
		descripcionFinalidad: 'Comercio, Turismo y Pymes',
		urlBasesReguladoras: null,
		fechaInicioSolicitud: '2026-10-02',
		fechaFinSolicitud: '2026-11-30',
		textInicio: null,
		textFin: null,
		documentos: [{ id: 10, descripcion: 'Extracto en castellano', nombreFic: 'extracto.pdf', long: 1000 }],
		...extra,
	};
}

/** Guardada ayer y ya leída por la IA */
const guardada = (extra: Partial<Ficha> = {}) => ({
	...aConvocatoria(fichaBdns(extra), AYER),
	ia: analisis({ plazoFin: '2026-11-30', plazo: 'Hasta el 30 de noviembre de 2026.' }),
});

describe('ficha de la BDNS', () => {
	it('se guarda con la fecha de comprobación y los documentos', () => {
		const c = aConvocatoria(fichaBdns(), HOY);
		expect(c.comprobada).toBe(HOY);
		expect(c.fin).toBe('2026-11-30');
		expect(c.documento).toEqual({ id: 10, nombre: 'extracto.pdf' });
		expect(c.docs).toEqual([10]);
	});

	it('una ficha incompleta o de otra convocatoria no vale', () => {
		expect(fichaValida(fichaBdns(), '900001')).toBe(true);
		expect(fichaValida(fichaBdns(), '900002')).toBe(false);
		expect(fichaValida(fichaBdns({ descripcion: '' }), '900001')).toBe(false);
		expect(fichaValida(fichaBdns({ fechaRecepcion: '' }), '900001')).toBe(false);
		expect(fichaValida({ ...fichaBdns(), documentos: undefined } as unknown as Ficha, '900001')).toBe(false);
		expect(fichaValida(null, '900001')).toBe(false);
	});
});

describe('actualizar una convocatoria guardada', () => {
	it('sin cambios: solo se apunta la comprobación y no se relee', () => {
		const antes = guardada();
		const { convocatoria, campos } = actualizar(antes, fichaBdns(), HOY);
		expect(campos).toEqual([]);
		expect(convocatoria.comprobada).toBe(HOY);
		expect(convocatoria.cambios).toBeUndefined();
		expect(convocatoria.iaRevisar).toBeUndefined();
		expect(convocatoria.ia).toEqual(antes.ia);
	});

	it('no modifica la guardada', () => {
		const antes = guardada();
		const copia = structuredClone(antes);
		actualizar(antes, fichaBdns({ fechaFinSolicitud: '2026-12-31' }), HOY);
		expect(antes).toEqual(copia);
	});

	it('si se amplía la fecha de cierre, manda la de la BDNS y la IA vuelve a leerla', () => {
		const antes = { ...guardada(), iaIntentos: 2, iaError: 'algo' };
		const { convocatoria, campos } = actualizar(antes, fichaBdns({ fechaFinSolicitud: '2026-12-31' }), HOY);
		expect(campos).toEqual(['fin']);
		expect(convocatoria.fin).toBe('2026-12-31');
		// La fecha que sacó la IA era la vieja: revisarCierreIa la aparta para que no tape la nueva
		expect(revisarCierreIa(convocatoria)).toBe(true);
		expect(convocatoria.ia?.plazoFin).toBeNull();
		expect(convocatoria.cierreDescartado).toEqual({ fecha: '2026-11-30', motivo: 'no coincide con la de la BDNS (2026-12-31)' });
		expect(convocatoria.ia?.resumen).toBe(antes.ia.resumen);
		expect(convocatoria.iaRevisar).toBe('cambió el plazo');
		expect(convocatoria.iaIntentos).toBeUndefined();
		expect(convocatoria.iaError).toBeUndefined();
		expect(convocatoria.cambios).toEqual([{ fecha: HOY, campos: ['fin'] }]);
	});

	it('un dato que la ficha deja de traer se conserva (no se borra la fecha de cierre)', () => {
		const { convocatoria, campos, conservados } = actualizar(guardada(), fichaBdns({ fechaFinSolicitud: null, presupuestoTotal: null }), HOY);
		expect(campos).toEqual([]);
		expect(conservados).toEqual(['presupuesto', 'fin']);
		expect(convocatoria.fin).toBe('2026-11-30');
		expect(convocatoria.presupuesto).toBe(100000);
		expect(convocatoria.iaRevisar).toBeUndefined();
	});

	it('detecta cambios de importe y de zona', () => {
		const { convocatoria, campos } = actualizar(guardada(), fichaBdns({ presupuestoTotal: 150000, regiones: [{ descripcion: 'ES - ESPAÑA' }] }), HOY);
		expect(campos).toEqual(['presupuesto', 'regiones']);
		expect(convocatoria.presupuesto).toBe(150000);
		expect(convocatoria.regiones).toEqual([{ codigo: 'ES', nombre: 'ESPAÑA' }]);
		// No toca el plazo ni el documento: no hace falta volver a leer las bases
		expect(convocatoria.iaRevisar).toBeUndefined();
	});

	it('un documento añadido se apunta, pero se sigue usando el de las bases', () => {
		const otro = { id: 11, descripcion: 'Corrección de errores', nombreFic: 'correccion.pdf', long: 500 };
		const { convocatoria, campos } = actualizar(guardada(), fichaBdns({ documentos: [otro, ...fichaBdns().documentos] }), HOY);
		expect(campos).toEqual(['documentos']);
		expect(convocatoria.documento?.id).toBe(10);
		expect(convocatoria.docs).toEqual([10, 11]);
		expect(convocatoria.iaRevisar).toBeUndefined();
	});

	it('si el documento de las bases desaparece, se usa el nuevo y la IA lo lee', () => {
		const nuevo = { id: 12, descripcion: 'Extracto en castellano', nombreFic: 'extracto-2.pdf', long: 900 };
		const { convocatoria, campos } = actualizar(guardada(), fichaBdns({ documentos: [nuevo] }), HOY);
		expect(campos).toEqual(['documento', 'documentos']);
		expect(convocatoria.documento).toEqual({ id: 12, nombre: 'extracto-2.pdf' });
		expect(convocatoria.iaRevisar).toBe('documento nuevo');
	});

	it('una sin leer que falló con un documento lo vuelve a intentar con el nuevo', () => {
		const antes = { ...guardada(), ia: null, iaIntentos: 3, iaError: 'no es un PDF', iaIntentadoEl: AYER };
		const nuevo = { id: 12, descripcion: 'Extracto en castellano', nombreFic: 'extracto.pdf', long: 900 };
		const { convocatoria } = actualizar(antes, fichaBdns({ documentos: [nuevo] }), HOY);
		expect(convocatoria.iaIntentos).toBeUndefined();
		expect(convocatoria.iaError).toBeUndefined();
		expect(convocatoria.iaRevisar).toBeUndefined();
	});

	it('datos guardados antes de las comprobaciones (sin lista de documentos): la primera vez solo se apunta', () => {
		const { docs, comprobada, ...antes } = guardada();
		const { convocatoria, campos } = actualizar(antes, fichaBdns(), HOY);
		expect(campos).toEqual([]);
		expect(convocatoria.docs).toEqual([10]);
		expect(convocatoria.comprobada).toBe(HOY);
	});

	it('guarda solo los últimos cambios', () => {
		let c = guardada();
		for (let i = 1; i <= 12; i++) c = { ...actualizar(c, fichaBdns({ presupuestoTotal: 100000 + i }), `2026-10-${String(i).padStart(2, '0')}`).convocatoria, ia: c.ia };
		expect(c.cambios).toHaveLength(10);
		expect(c.cambios!.at(-1)!.fecha).toBe('2026-10-12');
	});
});
