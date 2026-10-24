/**
 * Los datos viven en el repositorio (carpeta datos/): la Action los
 * actualiza cada mañana y, al subirlos, Vercel reconstruye la web.
 *   convocatorias.json  las abiertas (o sin fecha de cierre conocida)
 *   vistas.json         las descartadas, para no volver a pedir su ficha
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import type { Datos } from '../src/lib/tipos.ts';

const ruta = (nombre: string) => new URL(`../datos/${nombre}`, import.meta.url);

export function leerDatos(): Datos {
	const f = ruta('convocatorias.json');
	return existsSync(f) ? (JSON.parse(readFileSync(f, 'utf8')) as Datos) : { actualizado: '', convocatorias: [] };
}

export function guardarDatos(datos: Datos) {
	datos.convocatorias.sort((a, b) => b.publicada.localeCompare(a.publicada) || b.codigo.localeCompare(a.codigo));
	writeFileSync(ruta('convocatorias.json'), JSON.stringify(datos, null, '\t') + '\n');
}

/** Código BDNS → { motivo, fecha en que se descartó } */
export type Vistas = Record<string, { motivo: string; fecha: string }>;

export function leerVistas(): Vistas {
	const f = ruta('vistas.json');
	return existsSync(f) ? (JSON.parse(readFileSync(f, 'utf8')) as Vistas) : {};
}

export function guardarVistas(vistas: Vistas) {
	const ordenadas = Object.fromEntries(Object.entries(vistas).sort(([a], [b]) => b.localeCompare(a)));
	writeFileSync(ruta('vistas.json'), JSON.stringify(ordenadas, null, '\t') + '\n');
}

export const hoyIso = () => new Date().toISOString().slice(0, 10);
