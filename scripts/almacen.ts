/**
 * Los datos viven en el repositorio (carpeta datos/): la Action los
 * actualiza cada mañana y, al subirlos, Vercel reconstruye la web.
 *   convocatorias.json  las abiertas (o sin fecha de cierre conocida)
 *   vistas.json         las descartadas, para no volver a pedir su ficha
 *   cerradas.json       lo mínimo de las retiradas por cerradas, para su ficha («ha cerrado»)
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import type { Cerrada, Convocatoria, Datos } from '../src/lib/tipos.ts';
import { cierre } from '../src/lib/fechas.ts';

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

/** Días que se guarda una cerrada (y sigue su ficha) */
export const DIAS_CERRADAS = 180;

export function leerCerradas(): Record<string, Cerrada> {
	const f = ruta('cerradas.json');
	return existsSync(f) ? (JSON.parse(readFileSync(f, 'utf8')) as Record<string, Cerrada>) : {};
}

export function guardarCerradas(cerradas: Record<string, Cerrada>, hoy: string) {
	const corte = new Date(Date.parse(`${hoy}T00:00:00Z`) - DIAS_CERRADAS * 86_400_000).toISOString().slice(0, 10);
	const vigentes = Object.entries(cerradas)
		.filter(([, c]) => c.retirada >= corte)
		.sort(([a], [b]) => b.localeCompare(a));
	writeFileSync(ruta('cerradas.json'), JSON.stringify(Object.fromEntries(vigentes), null, '\t') + '\n');
}

/** Lo mínimo de una convocatoria que se retira por cerrada */
export function aCerrada(c: Convocatoria, hoy: string): Cerrada {
	return {
		codigo: c.codigo,
		titulo: c.titulo,
		resumen: c.ia?.titular ?? c.ia?.resumen ?? null,
		organo: [c.organo.nivel3, c.organo.nivel2].filter(Boolean).join(' — '),
		cierre: cierre(c) ?? c.fin ?? hoy,
		enlace: c.enlace,
		retirada: hoy,
	};
}

export const hoyIso = () => new Date().toISOString().slice(0, 10);
