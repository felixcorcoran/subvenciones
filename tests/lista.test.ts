import { describe, expect, it } from 'vitest';
import { cercania, coincide, COMUNIDADES, comunidadDe, deComunidad, diasHasta, SECTORES, seccionesDe, slug, textoMotivo, titularDe } from '../src/lib/lista';
import { hoyMadrid } from '../src/lib/fechas';
import { analisis, convocatoria, perfil } from './ayudas';

describe('presentación de las listas', () => {
	it('titular: el corto de la IA, si no el resumen, si no el título oficial', () => {
		expect(titularDe(convocatoria({ ia: analisis({ titular: 'Digitalizar el comercio andaluz' }) }))).toBe('Digitalizar el comercio andaluz');
		expect(titularDe(convocatoria())).toBe('Ayudas para digitalizar el comercio local.');
		expect(titularDe(convocatoria({ ia: null }))).toBe('Ayudas a la digitalización del comercio');
	});

	it('slugs y catálogos de comunidades y sectores', () => {
		expect(slug('Comunidad de Madrid')).toBe('comunidad-de-madrid');
		expect(slug('Castilla-La Mancha')).toBe('castilla-la-mancha');
		expect(COMUNIDADES).toHaveLength(19);
		expect(new Set(COMUNIDADES.map((c) => c.slug)).size).toBe(19);
		expect(SECTORES.find((s) => s.letra === 'T')?.slug).toBe('otros-servicios');
		expect(new Set(SECTORES.map((s) => s.slug)).size).toBe(SECTORES.length);
	});

	it('comunidad y sectores de una ayuda', () => {
		const andaluza = convocatoria({ regiones: [{ codigo: 'ES617', nombre: 'Málaga' }] });
		expect(deComunidad(andaluza, 'ES61')).toBe(true);
		expect(comunidadDe(andaluza)?.nombre).toBe('Andalucía');
		expect(comunidadDe(convocatoria({ regiones: [{ codigo: 'ES', nombre: 'ESPAÑA' }] }))).toBeNull();
		expect(seccionesDe(convocatoria({ sectores: [{ codigo: '47', nombre: 'Comercio al por menor' }] }))).toEqual(['G']);
	});

	it('cercanía: municipio, provincia, comunidad, toda España y otras zonas', () => {
		const p = perfil({ provincia: 'ES617', municipio: 'Marbella' });
		const conZona = (codigo: string) => convocatoria({ regiones: [{ codigo, nombre: '' }] });
		expect(cercania(conZona('ES617'), p, 'Marbella')).toBe(0);
		expect(cercania(conZona('ES617'), p, null)).toBe(1);
		expect(cercania(conZona('ES61'), p, null)).toBe(2);
		expect(cercania(conZona('ES'), p, null)).toBe(4);
		expect(cercania(conZona('ES300'), p, null)).toBe(5);
	});

	it('explica «Para toda España» si la convoca un ayuntamiento', () => {
		expect(textoMotivo({ tipo: 'bien', texto: 'Para toda España' }, 'Cabra')).toContain('ayuntamiento');
		expect(textoMotivo({ tipo: 'bien', texto: 'Para toda España' }, null)).toBe('Para toda España');
	});

	it('búsqueda sin tildes ni mayúsculas y con todas las palabras', () => {
		const c = convocatoria();
		expect(coincide(c, 'DIGITALIZACION comercio')).toBe(true);
		expect(coincide(c, 'digitalización hostelería')).toBe(false);
		expect(coincide(c, '  ')).toBe(true);
	});

	it('días hasta el cierre y el día de hoy en Madrid', () => {
		expect(diasHasta(convocatoria({ fin: '2026-10-30' }), '2026-10-25')).toBe(5);
		expect(diasHasta(convocatoria({ fin: null }), '2026-10-25')).toBeNull();
		// 23:30 UTC del 31 de octubre: en Madrid ya es 1 de noviembre
		expect(hoyMadrid(new Date('2026-10-31T23:30:00Z'))).toBe('2026-11-01');
		expect(hoyMadrid(new Date('2026-07-15T21:59:00Z'))).toBe('2026-07-15');
	});
});
