import { describe, expect, it } from 'vitest';
import { SECCIONES, seccionDe, nombreSeccion } from '../src/lib/sectores';
import { nombreZona } from '../src/lib/zonas';
import provincias from '../src/data/provincias.json';

describe('sectores: códigos de la BDNS a su sección de la CNAE-2025', () => {
	it('las 22 secciones, de la A a la V, sin huecos en las divisiones', () => {
		expect(SECCIONES.map((s) => s.letra).join('')).toBe('ABCDEFGHIJKLMNOPQRSTUV');
		for (let i = 1; i < SECCIONES.length; i++) expect(SECCIONES[i].desde).toBeGreaterThan(SECCIONES[i - 1].hasta);
	});

	it('una letra es su propia sección (en mayúscula o minúscula)', () => {
		expect(seccionDe('G')).toBe('G');
		expect(seccionDe(' i ')).toBe('I');
	});

	it('las divisiones van a su sección según la CNAE-2025, no la de 2009', () => {
		expect(seccionDe('47')).toBe('G'); // comercio al por menor
		expect(seccionDe('56')).toBe('I'); // comidas y bebidas
		expect(seccionDe('60')).toBe('J'); // en 2025, programación y radiodifusión
		expect(seccionDe('63')).toBe('K'); // en 2025, infraestructura informática
		expect(seccionDe('94.1')).toBe('T'); // en 2025, asociativas = otros servicios
		expect(seccionDe('01.4')).toBe('A');
	});

	it('lo que no reconoce devuelve null', () => {
		expect(seccionDe('XX')).toBeNull();
		expect(seccionDe('04')).toBeNull(); // no existe la división 04
	});

	it('nombre de la sección, o la letra si no la conoce', () => {
		expect(nombreSeccion('I')).toBe('Hostelería');
		expect(nombreSeccion('Z')).toBe('Z');
	});
});

describe('zonas: nombres bien escritos', () => {
	it('la BDNS escribe las comunidades en mayúsculas sin tildes; aquí salen bien', () => {
		expect(nombreZona('ES21', 'PAIS VASCO')).toBe('País Vasco');
		expect(nombreZona('ES61', 'ANDALUCIA')).toBe('Andalucía');
		expect(nombreZona('ES617', 'Málaga')).toBe('Málaga');
		expect(nombreZona('ES', 'ESPAÑA')).toBe('toda España');
	});

	it('si no conoce el código, deja el nombre original', () => {
		expect(nombreZona('FR10', 'Île-de-France')).toBe('Île-de-France');
	});

	it('las 59 provincias e islas que usa la BDNS, cada una con su comunidad', () => {
		expect(provincias).toHaveLength(59);
		for (const p of provincias) expect(p.codigo.startsWith(p.codigoComunidad)).toBe(true);
	});
});
