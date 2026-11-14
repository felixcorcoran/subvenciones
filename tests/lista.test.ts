import { describe, expect, it } from 'vitest';
import { otrasLineas, paraComunidad, cercania, coincide, deProvincia, deTodaLaComunidad, PROVINCIAS, provinciasDe, COMUNIDADES, comunidadDe, deComunidad, diasHasta, SECTORES, seccionesDe, slug, titularDe } from '../src/lib/lista';
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

	it('provincias: catálogo, slugs únicos y a qué provincia es cada ayuda', () => {
		expect(PROVINCIAS).toHaveLength(59);
		expect(new Set(PROVINCIAS.map((p) => p.slug)).size).toBe(59);
		expect(PROVINCIAS.find((p) => p.codigo === 'ES521')?.slug).toBe('alicante-alacant');
		expect(provinciasDe('ES61')).toHaveLength(8);
		expect(provinciasDe('ES30')).toEqual([]);
		const malaguena = convocatoria({ regiones: [{ codigo: 'ES617', nombre: 'Málaga' }] });
		expect(deProvincia(malaguena, 'ES617')).toBe(true);
		expect(deProvincia(malaguena, 'ES618')).toBe(false);
		expect(deTodaLaComunidad(convocatoria({ regiones: [{ codigo: 'ES61', nombre: 'Andalucía' }] }), 'ES61')).toBe(true);
		expect(deTodaLaComunidad(malaguena, 'ES61')).toBe(false);
	});

	it('búsqueda por municipio: el del ayuntamiento que convoca', () => {
		const c = convocatoria({ organo: { nivel1: 'LOCAL', nivel2: 'CABRA', nivel3: 'AYUNTAMIENTO DE CABRA' } });
		expect(coincide(c, 'cabra')).toBe(true);
		expect(coincide(convocatoria({ ia: null }), 'marbella')).toBe(false);
	});

	it('la lista de una comunidad: las suyas, las de su grupo, las estatales y las sin zona', () => {
		const con = (codigo?: string) => convocatoria({ regiones: codigo ? [{ codigo, nombre: '' }] : [] });
		expect(paraComunidad(con('ES617'), 'ES61')).toBe(true);
		expect(paraComunidad(con('ES61'), 'ES61')).toBe(true);
		expect(paraComunidad(con('ES6'), 'ES61')).toBe(true);
		expect(paraComunidad(con('ES'), 'ES61')).toBe(true);
		expect(paraComunidad(con(), 'ES61')).toBe(true);
		expect(paraComunidad(con('ES300'), 'ES61')).toBe(false);
		expect(paraComunidad(con('ES62'), 'ES61')).toBe(false);
	});

	it('otras líneas de la misma convocatoria: mismo órgano y título casi igual', () => {
		const organo = { nivel1: 'LOCAL', nivel2: 'ALMERIA', nivel3: 'AYUNTAMIENTO DE ALMERIA' };
		const a = convocatoria({ codigo: '1', organo, titulo: 'Decreto 89/2026 de subvenciones para proyectos audiovisuales en 2026' });
		const b = convocatoria({ codigo: '2', organo, titulo: 'DECRETO 89/2026 de subvenciones para proyectos audiovisuales, en 2026' });
		const otroConcurso = convocatoria({ codigo: '3', organo, titulo: 'Acuerdo de 30 de octubre por el que se aprueba el concurso de villancicos' });
		const parecido = convocatoria({ codigo: '4', organo, titulo: 'Acuerdo de 30 de octubre por el que se aprueba el concurso de belenes' });
		const otroOrgano = convocatoria({ codigo: '5', titulo: a.titulo });
		expect(otrasLineas(a, [a, b, otroConcurso, otroOrgano]).map((x) => x.codigo)).toEqual(['2']);
		expect(otrasLineas(otroConcurso, [parecido])).toEqual([]);
	});
});
