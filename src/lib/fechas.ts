/**
 * Fecha de cierre y fechas en texto, sin más dependencias: las usan la web y
 * los scripts de Node (que no pueden importar los JSON que usa encaje.ts).
 * encaje.ts las reexporta tal cual.
 */
import type { ConvocatoriaWeb as Convocatoria } from './tipos.ts';

/**
 * Fecha de cierre conocida (AAAA-MM-DD), de la IA o de la propia ficha. En
 * `ia.plazoFin` solo queda una fecha de las bases que no contradice a la BDNS
 * ni es dudosa (scripts/lectura.ts, revisarCierreIa).
 */
export const cierre = (c: Convocatoria) => c.ia?.plazoFin ?? c.fin ?? null;

export const MESES_NOMBRE = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

export function fechaLarga(iso: string): string {
	const [a, m, d] = iso.split('-').map(Number);
	return `${d} de ${MESES_NOMBRE[m - 1]} de ${a}`;
}

/** Si el texto del plazo nombra esa fecha («31 de enero», «31/01/2027»…) */
export function mencionaFecha(texto: string, fecha: string): boolean {
	const [, mes, dia] = fecha.split('-').map(Number);
	const larga = new RegExp(`(^|\\D)0?${dia}\\s+de\\s+${MESES_NOMBRE[mes - 1]}`, 'i');
	const corta = new RegExp(`(^|\\D)0?${dia}[/.-]0?${mes}([/.-]|\\D|$)`);
	return larga.test(texto) || corta.test(texto);
}

/**
 * Plazo previo al periodo de la BDNS: las bases ponen al negocio una fecha
 * límite (por ejemplo, la adhesión de comercios a una campaña de bonos)
 * anterior a las fechas de la ficha de la BDNS, que son de otra fase. Solo si
 * se cumplen las tres cosas a la vez:
 * - la fecha de las bases es anterior al inicio que da la BDNS,
 * - no es anterior al registro de la convocatoria,
 * - el texto del plazo leído en las bases nombra esa fecha.
 * Entonces esa fecha es el cierre para el negocio, y las de la BDNS se
 * conservan como el periodo oficial de la convocatoria.
 */
export function plazoPrevio(c: Convocatoria): boolean {
	const fecha = c.ia?.plazoFin;
	if (!fecha || !/^\d{4}-\d{2}-\d{2}$/.test(fecha) || !c.inicio) return false;
	return fecha < c.inicio && fecha >= c.publicada && mencionaFecha(c.ia!.plazo ?? '', fecha);
}
