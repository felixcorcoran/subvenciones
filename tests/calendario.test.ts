import { describe, expect, it } from 'vitest';
import { eventoIcs } from '../src/lib/calendario';
import { analisis, convocatoria } from './ayudas';

const FICHA = 'https://subvenciones.felixcorcoran.es/ayudas/900001';
const lineas = (ics: string) => ics.split('\r\n');
/** Las líneas del .ics ya desplegadas (una línea partida sigue con un espacio delante) */
const desplegar = (ics: string) => ics.replace(/\r\n /g, '');

describe('plazo al calendario (.ics)', () => {
	it('sin fecha de cierre no hay evento', () => {
		expect(eventoIcs(convocatoria({ fin: null, ia: analisis({ plazoFin: null }) }), FICHA)).toBeNull();
	});

	it('es un evento de todo el día de cierre, con aviso tres días antes', () => {
		const ics = eventoIcs(convocatoria({ fin: '2026-12-31' }), FICHA)!;
		const texto = desplegar(ics);
		expect(texto).toContain('DTSTART;VALUE=DATE:20261231');
		// El día siguiente, aunque cambie de año
		expect(texto).toContain('DTEND;VALUE=DATE:20270101');
		expect(texto).toContain('TRIGGER:-P3D');
		expect(texto).toContain(`URL:${FICHA}`);
	});

	it('usa la fecha que la IA ha leído en las bases si la hay', () => {
		const ics = eventoIcs(convocatoria({ fin: '2026-12-31', ia: analisis({ plazoFin: '2026-11-15' }) }), FICHA)!;
		expect(desplegar(ics)).toContain('DTSTART;VALUE=DATE:20261115');
	});

	it('termina las líneas en CRLF y ninguna pasa de 75 octetos', () => {
		const ics = eventoIcs(convocatoria({ titulo: 'Convocatoria '.repeat(20), ia: null }), FICHA)!;
		expect(ics.endsWith('\r\n')).toBe(true);
		expect(ics.replace(/\r\n/g, '')).not.toContain('\n');
		for (const l of lineas(ics)) expect(new TextEncoder().encode(l).length).toBeLessThanOrEqual(75);
	});

	it('parte las líneas sin romper letras con tilde', () => {
		const ics = eventoIcs(convocatoria({ titulo: 'Subvención para la promoción económica de Málaga '.repeat(4), ia: null }), FICHA)!;
		expect(desplegar(ics)).toContain('Subvención para la promoción económica de Málaga');
	});

	it('escapa comas, puntos y comas y saltos de línea', () => {
		const ics = eventoIcs(convocatoria({ titulo: 'Bonos; comercio, hostelería', ia: null }), FICHA)!;
		expect(desplegar(ics)).toContain('SUMMARY:Cierra el plazo: Bonos\\; comercio\\, hostelería');
	});
});
