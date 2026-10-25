import { describe, expect, it } from 'vitest';
import { encaje, normalizarMunicipio, nombreMunicipio, municipioDe, cierre } from '../src/lib/encaje';
import { analisis, convocatoria, HOY, perfil } from './ayudas';

const motivos = (r: ReturnType<typeof encaje>, tipo: 'bien' | 'revisa' | 'no') => r.motivos.filter((m) => m.tipo === tipo).map((m) => m.texto);

describe('encaje: lo normal', () => {
	it('una ayuda de su comunidad, abierta y sin pegas, se puede pedir', () => {
		const r = encaje(convocatoria(), perfil(), HOY);
		expect(r.estado).toBe('si');
		expect(motivos(r, 'bien')).toContain('Es para tu zona');
	});

	it('basta con que algo no encaje para que no encaje', () => {
		const r = encaje(convocatoria({ ia: analisis({}, { autonomos: 'no' }) }), perfil(), HOY);
		expect(r.estado).toBe('no');
		expect(motivos(r, 'no')).toContain('No admite autónomos');
	});

	it('lo que no se sabe deja la ayuda en «Revisa», nunca en «Puedes pedirla»', () => {
		const r = encaje(convocatoria({ ia: null }), perfil(), HOY);
		expect(r.estado).toBe('revisa');
		expect(motivos(r, 'revisa')[0]).toMatch(/Aún no hemos leído las bases/);
	});

	it('las dudas de la IA también mandan a «Revisa»', () => {
		const r = encaje(convocatoria({ ia: analisis({ dudas: 'No queda claro si vale para franquicias.' }) }), perfil(), HOY);
		expect(r.estado).toBe('revisa');
	});
});

describe('encaje: plazo', () => {
	it('cerrada si la fecha de cierre ya pasó', () => {
		const r = encaje(convocatoria({ fin: '2026-10-20' }), perfil(), HOY);
		expect(r.estado).toBe('no');
		expect(motivos(r, 'no')[0]).toBe('El plazo terminó el 20 de octubre de 2026');
	});

	it('la fecha que lee la IA en las bases manda sobre la de la ficha', () => {
		const c = convocatoria({ fin: '2026-12-31', ia: analisis({ plazoFin: '2026-10-01' }) });
		expect(cierre(c)).toBe('2026-10-01');
		expect(encaje(c, perfil(), HOY).estado).toBe('no');
	});

	it('el último día todavía se puede pedir', () => {
		expect(encaje(convocatoria({ fin: HOY }), perfil(), HOY).estado).toBe('si');
	});
});

describe('encaje: zona (códigos NUTS)', () => {
	it('una ayuda estatal vale para cualquier provincia', () => {
		const r = encaje(convocatoria({ regiones: [{ codigo: 'ES', nombre: 'ESPAÑA' }] }), perfil(), HOY);
		expect(motivos(r, 'bien')).toContain('Para toda España');
	});

	it('la provincia exacta encaja', () => {
		const r = encaje(convocatoria({ regiones: [{ codigo: 'ES617', nombre: 'Málaga' }] }), perfil(), HOY);
		expect(r.estado).toBe('si');
	});

	it('otra comunidad no encaja, y lo dice con su nombre bien escrito', () => {
		const r = encaje(convocatoria({ regiones: [{ codigo: 'ES21', nombre: 'PAIS VASCO' }] }), perfil(), HOY);
		expect(r.estado).toBe('no');
		expect(motivos(r, 'no')).toContain('Solo para negocios de País Vasco');
	});

	it('sin zona, «Revisa»', () => {
		expect(encaje(convocatoria({ regiones: [] }), perfil(), HOY).estado).toBe('revisa');
	});
});

describe('encaje: municipio', () => {
	const local = convocatoria({
		organo: { nivel1: 'LOCAL', nivel2: 'MARBELLA', nivel3: 'AYUNTAMIENTO DE MARBELLA' },
		regiones: [{ codigo: 'ES617', nombre: 'Málaga' }],
	});

	it('si no dice su municipio, una ayuda de ayuntamiento queda en «Revisa»', () => {
		const r = encaje(local, perfil(), HOY);
		expect(r.estado).toBe('revisa');
		expect(motivos(r, 'revisa')[0]).toBe('Es de Marbella: normalmente solo para negocios de allí');
	});

	it('del mismo municipio, encaja aunque lo escriba con otras mayúsculas', () => {
		expect(encaje(local, perfil({ municipio: 'marbella' }), HOY).estado).toBe('si');
	});

	it('de otro municipio, no', () => {
		const r = encaje(local, perfil({ municipio: 'Estepona' }), HOY);
		expect(motivos(r, 'no')).toContain('Solo para negocios de Marbella');
	});

	it('los municipios con artículo detrás («CARBALLIÑO, O») se comparan bien', () => {
		const c = convocatoria({
			organo: { nivel1: 'LOCAL', nivel2: 'CARBALLIÑO, O', nivel3: 'AYUNTAMIENTO DE CARBALLIÑO, O' },
			regiones: [{ codigo: 'ES113', nombre: 'Ourense' }],
		});
		expect(municipioDe(c)).toBe('O Carballiño');
		expect(encaje(c, perfil({ provincia: 'ES113', municipio: 'O Carballiño' }), HOY).estado).toBe('si');
	});

	it('una diputación no limita a un municipio', () => {
		const c = convocatoria({ organo: { nivel1: 'LOCAL', nivel2: 'MÁLAGA', nivel3: 'DIPUTACIÓN PROVINCIAL DE MÁLAGA' } });
		expect(municipioDe(c)).toBeNull();
	});
});

describe('encaje: tamaño, forma y antigüedad', () => {
	it('una gran empresa no entra en una ayuda solo para pymes', () => {
		const r = encaje(convocatoria(), perfil({ forma: 'sociedad', empleados: 250 }), HOY);
		expect(motivos(r, 'no')).toContain('Solo para pymes y autónomos');
	});

	it('plantilla por encima del máximo: no; por debajo: sí; en medio: «Revisa»', () => {
		const tope = (n: number) => convocatoria({ ia: analisis({}, { maxEmpleados: n }) });
		expect(encaje(tope(5), perfil({ empleados: 10 }), HOY).estado).toBe('no');
		expect(encaje(tope(49), perfil({ empleados: 10 }), HOY).estado).toBe('si');
		expect(encaje(tope(5), perfil({ empleados: 1 }), HOY).estado).toBe('revisa');
	});

	it('quien aún no ha empezado no entra si hay que tener el negocio en marcha', () => {
		const c = convocatoria({ ia: analisis({}, { emprendedores: 'no' }) });
		const r = encaje(c, perfil({ forma: 'emprender', antiguedad: 'nada' }), HOY);
		expect(motivos(r, 'no')).toContain('Hay que tener ya el negocio en marcha');
	});

	it('antigüedad mínima: con menos de un año no llega a dos años', () => {
		const c = convocatoria({ ia: analisis({}, { antiguedadMinMeses: 24 }) });
		const r = encaje(c, perfil({ antiguedad: 'menos1' }), HOY);
		expect(motivos(r, 'no')).toContain('Pide al menos 2 años de actividad');
		expect(encaje(c, perfil({ antiguedad: 'mas3' }), HOY).estado).toBe('si');
	});

	it('ayudas solo para negocios nuevos: con más de tres años no', () => {
		const c = convocatoria({ ia: analisis({}, { antiguedadMaxMeses: 12 }) });
		expect(encaje(c, perfil({ antiguedad: 'mas3' }), HOY).estado).toBe('no');
		expect(encaje(c, perfil({ antiguedad: 'menos1' }), HOY).estado).toBe('si');
	});
});

describe('encaje: sector (CNAE-2025)', () => {
	it('sin sectores o con muchos, vale para cualquiera', () => {
		expect(motivos(encaje(convocatoria(), perfil(), HOY), 'bien')).toContain('Abierta a cualquier sector');
	});

	it('su sección entera: encaja', () => {
		const c = convocatoria({ sectores: [{ codigo: 'I', nombre: 'HOSTELERÍA' }] });
		expect(motivos(encaje(c, perfil(), HOY), 'bien')).toContain('Es para tu sector');
	});

	it('solo una parte de su sección: «Revisa»', () => {
		const c = convocatoria({ sectores: [{ codigo: '56', nombre: 'Servicios de comidas y bebidas' }] });
		expect(encaje(c, perfil(), HOY).estado).toBe('revisa');
	});

	it('otro sector: no', () => {
		const c = convocatoria({ sectores: [{ codigo: 'A', nombre: 'AGRICULTURA' }] });
		expect(encaje(c, perfil(), HOY).estado).toBe('no');
	});

	it('si no sabe su sector y la ayuda es de algunos, «Revisa»', () => {
		const c = convocatoria({ sectores: [{ codigo: 'A', nombre: 'AGRICULTURA' }] });
		expect(encaje(c, perfil({ sector: '' }), HOY).estado).toBe('revisa');
	});
});

describe('municipios: escritura', () => {
	it('normaliza tildes, mayúsculas y el artículo pospuesto', () => {
		expect(normalizarMunicipio('Vélez-Málaga')).toBe('velez malaga');
		expect(normalizarMunicipio('CARBALLIÑO, O')).toBe('o carballino');
		expect(normalizarMunicipio('Carballiño, O')).toBe(normalizarMunicipio('O CARBALLINO'));
	});

	it('muestra el nombre con mayúsculas de nombre propio', () => {
		expect(nombreMunicipio('ALHAURIN DE LA TORRE')).toBe('Alhaurin de la Torre');
		expect(nombreMunicipio('ROZAS DE MADRID, LAS')).toBe('Las Rozas de Madrid');
	});
});
