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

/** Las líneas de un evento (VEVENT) con el plazo, o null si no hay fecha de cierre */
function evento(c: ConvocatoriaWeb, ficha: string): string[] | null {
	const fin = cierre(c);
	if (!fin) return null;
	const nombre = c.ia?.titular ?? c.ia?.resumen ?? c.titulo;
	const corto = nombre.length > 90 ? `${nombre.slice(0, 87).trimEnd()}…` : nombre;
	return [
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
	];
}

const calendario = (eventos: string[][], cabecera: string[] = []) =>
	[
		'BEGIN:VCALENDAR',
		'VERSION:2.0',
		'PRODID:-//felixcorcoran.es//Radar de subvenciones//ES',
		'CALSCALE:GREGORIAN',
		...cabecera,
		...eventos.flat(),
		'END:VCALENDAR',
	]
		.map(plegar)
		.join('\r\n') + '\r\n';

export function eventoIcs(c: ConvocatoriaWeb, ficha: string): string | null {
	const e = evento(c, ficha);
	return e ? calendario([e]) : null;
}

/**
 * Un calendario con el plazo de varias ayudas (los de una comunidad), para
 * suscribirse una vez: el programa de calendario lo vuelve a pedir cada día y
 * van apareciendo las nuevas. `fichaDe` da la dirección de cada ficha.
 */
export function calendarioIcs(lista: ConvocatoriaWeb[], fichaDe: (c: ConvocatoriaWeb) => string, nombre: string): string {
	const eventos = lista.map((c) => evento(c, fichaDe(c))).filter((e): e is string[] => e !== null);
	return calendario(eventos, [
		`X-WR-CALNAME:${escapar(nombre)}`,
		'X-WR-TIMEZONE:Europe/Madrid',
		'REFRESH-INTERVAL;VALUE=DURATION:PT12H',
		'X-PUBLISHED-TTL:PT12H',
	]);
}
