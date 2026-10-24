/**
 * El plazo de una convocatoria como evento de calendario (.ics): todo el día
 * de cierre, con aviso tres días antes. Sirve para Google Calendar, Apple y
 * Outlook sin guardar nada de nadie.
 */
import type { ConvocatoriaWeb } from './tipos.ts';
import { cierre } from './encaje.ts';

const escapar = (s: string) => s.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/([,;])/g, '\\$1');

/** Las líneas de un .ics no deben pasar de 75 octetos: se parten con un espacio delante */
function plegar(linea: string): string {
	const bytes = new TextEncoder().encode(linea);
	if (bytes.length <= 75) return linea;
	const partes: string[] = [];
	let actual = '';
	for (const ch of linea) {
		if (new TextEncoder().encode(actual + ch).length > (partes.length ? 74 : 75)) {
			partes.push(actual);
			actual = '';
		}
		actual += ch;
	}
	partes.push(actual);
	return partes.join('\r\n ');
}

const dia = (iso: string) => iso.replaceAll('-', '');
function siguiente(iso: string): string {
	const d = new Date(`${iso}T12:00:00Z`);
	d.setUTCDate(d.getUTCDate() + 1);
	return d.toISOString().slice(0, 10);
}

export function eventoIcs(c: ConvocatoriaWeb, ficha: string): string | null {
	const fin = cierre(c);
	if (!fin) return null;
	const nombre = c.ia?.resumen ?? c.titulo;
	const corto = nombre.length > 90 ? `${nombre.slice(0, 87).trimEnd()}…` : nombre;
	const lineas = [
		'BEGIN:VCALENDAR',
		'VERSION:2.0',
		'PRODID:-//felixcorcoran.es//Radar de subvenciones//ES',
		'CALSCALE:GREGORIAN',
		'BEGIN:VEVENT',
		`UID:bdns-${c.codigo}@subvenciones.felixcorcoran.es`,
		`DTSTAMP:${new Date().toISOString().replace(/[-:]/g, '').slice(0, 15)}Z`,
		`DTSTART;VALUE=DATE:${dia(fin)}`,
		`DTEND;VALUE=DATE:${dia(siguiente(fin))}`,
		`SUMMARY:${escapar(`Cierra el plazo: ${corto}`)}`,
		`DESCRIPTION:${escapar(`${c.titulo}\n\nFicha: ${ficha}\nConvocatoria oficial: ${c.enlace}`)}`,
		`URL:${ficha}`,
		'BEGIN:VALARM',
		'ACTION:DISPLAY',
		`DESCRIPTION:${escapar(`Quedan 3 días para pedir: ${corto}`)}`,
		'TRIGGER:-P3D',
		'END:VALARM',
		'END:VEVENT',
		'END:VCALENDAR',
	];
	return lineas.map(plegar).join('\r\n') + '\r\n';
}
