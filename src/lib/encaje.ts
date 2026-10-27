/**
 * ¿Puede este negocio pedir esta ayuda? Compara el perfil del formulario con
 * lo que dice la convocatoria (zona, sector y beneficiarios, de la BDNS) y
 * con lo que la IA ha leído en sus bases (autónomos o sociedades, plantilla,
 * antigüedad, municipio).
 *
 * Es una orientación, no un veredicto: cuando un dato no consta o no se puede
 * saber con el formulario, la ayuda queda en «Revisa», nunca en «Puedes
 * pedirla». Cada resultado lleva sus motivos, para que se vea por qué.
 */
import type { ConvocatoriaWeb as Convocatoria } from './tipos.ts';
import { nombreSeccion, seccionDe } from './sectores.ts';
import { nombreZona } from './zonas.ts';
import { cierre, fechaLarga } from './fechas.ts';

export type Forma = 'autonomo' | 'sociedad' | 'emprender';
export type Antiguedad = 'nada' | 'menos1' | '1a3' | 'mas3';

export interface Perfil {
	provincia: string;
	municipio: string;
	forma: Forma;
	/** Trabajadores: el mínimo del tramo elegido (0, 1, 10, 50 o 250) */
	empleados: number;
	antiguedad: Antiguedad;
	/** Letra de la sección CNAE, o '' si no lo sabe */
	sector: string;
}

export type Estado = 'si' | 'revisa' | 'no';
export interface Motivo {
	tipo: 'bien' | 'revisa' | 'no';
	texto: string;
}
export interface Encaje {
	estado: Estado;
	motivos: Motivo[];
}

/** Tramos de plantilla del formulario: [mínimo, máximo] */
const TRAMOS: Record<number, [number, number]> = { 0: [0, 0], 1: [1, 9], 10: [10, 49], 50: [50, 249], 250: [250, Infinity] };
/** Meses de actividad de cada respuesta: [mínimo, máximo] */
const MESES: Record<Antiguedad, [number, number]> = { nada: [0, 0], menos1: [0, 11], '1a3': [12, 36], mas3: [37, Infinity] };

const sinTildes = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '');

/** «CARBALLIÑO, O» → «o carballino»; «Málaga» → «malaga» */
export function normalizarMunicipio(s: string): string {
	let t = sinTildes(s).toLowerCase().trim();
	const m = t.match(/^(.*),\s*(el|la|los|las|o|a|os|as|l'|es|sa|ses|els|les)$/);
	if (m) t = `${m[2]} ${m[1]}`;
	return t.replace(/[^a-z0-9ñ ]+/g, ' ').replace(/\s+/g, ' ').trim();
}

/** «CARBALLIÑO, O» → «O Carballiño», para mostrarlo */
export function nombreMunicipio(s: string): string {
	let t = s.trim();
	const m = t.match(/^(.*),\s*(el|la|los|las|o|a|os|as|l'|es|sa|ses|els|les)$/i);
	if (m) t = `${m[2]} ${m[1]}`;
	const enlaces = new Set(['de', 'del', 'la', 'las', 'los', 'el', 'y', 'i', "d'", "l'"]);
	return t
		.toLowerCase()
		.split(' ')
		.map((w, i) => (i > 0 && enlaces.has(w) ? w : w.charAt(0).toUpperCase() + w.slice(1)))
		.join(' ');
}

/** El municipio al que se limita la ayuda, si se sabe */
export function municipioDe(c: Convocatoria): string | null {
	if (c.organo.nivel1 === 'LOCAL' && /^AYUNTAMIENTO|^AJUNTAMENT|^CONCELLO|^UDALA/i.test(c.organo.nivel3)) return nombreMunicipio(c.organo.nivel2);
	return c.ia?.condiciones.municipio ?? null;
}

// Fecha de cierre conocida y fechas en texto: en fechas.ts, sin dependencias
export { cierre, fechaLarga };

export function encaje(c: Convocatoria, p: Perfil, hoy = new Date().toISOString().slice(0, 10)): Encaje {
	const motivos: Motivo[] = [];
	const bien = (texto: string) => motivos.push({ tipo: 'bien', texto });
	const revisa = (texto: string) => motivos.push({ tipo: 'revisa', texto });
	const no = (texto: string) => motivos.push({ tipo: 'no', texto });

	// Plazo
	const fin = cierre(c);
	if (fin && fin < hoy) no(`El plazo terminó el ${fechaLarga(fin)}`);

	// Zona (códigos NUTS: ES, ES6, ES61, ES617…)
	const zonas = c.regiones.filter((r) => r.codigo.startsWith('ES'));
	if (zonas.length === 0) revisa('No indica la zona: comprueba en las bases dónde tiene que estar el negocio');
	else if (zonas.some((r) => p.provincia.startsWith(r.codigo))) {
		bien(zonas.some((r) => r.codigo === 'ES') ? 'Para toda España' : 'Es para tu zona');
	} else no(`Solo para negocios de ${zonas.map((r) => nombreZona(r.codigo, r.nombre)).join(', ')}`);

	// Municipio
	const municipio = municipioDe(c);
	if (municipio) {
		if (!p.municipio.trim()) revisa(`Es de ${municipio}: normalmente solo para negocios de allí`);
		else {
			const a = normalizarMunicipio(municipio);
			const b = normalizarMunicipio(p.municipio);
			if (a === b || a.includes(b) || b.includes(a)) bien('Es de tu municipio');
			else no(`Solo para negocios de ${municipio}`);
		}
	}

	// Tamaño de empresa (tipos de beneficiario de la BDNS)
	const [minEmp, maxEmp] = TRAMOS[p.empleados] ?? [0, 0];
	const pyme = c.beneficiarios.some((b) => /PYME|SIN INFORMACION/i.test(b));
	const grande = c.beneficiarios.some((b) => /GRAN EMPRESA|SIN INFORMACION/i.test(b));
	if (minEmp >= 250 && !grande) no('Solo para pymes y autónomos');
	if (minEmp < 250 && !pyme && grande) no('Solo para grandes empresas');

	// Sector
	const secciones = [...new Set(c.sectores.map((s) => seccionDe(s.codigo)).filter((s): s is string => Boolean(s)))];
	if (secciones.length === 0 || secciones.length >= 8) bien('Abierta a cualquier sector');
	else if (!p.sector) revisa(`Solo para algunos sectores: ${secciones.map(nombreSeccion).join(', ')}`);
	else if (secciones.includes(p.sector)) {
		const propios = c.sectores.filter((s) => seccionDe(s.codigo) === p.sector);
		if (propios.some((s) => /^[A-V]$/i.test(s.codigo.trim()))) bien('Es para tu sector');
		else revisa(`Solo para parte de tu sector: ${propios.map((s) => s.nombre).join(', ')}`);
	} else no(`Es para otro sector: ${secciones.map(nombreSeccion).join(', ')}`);

	// Lo que dicen las bases
	const ia = c.ia;
	if (!ia) {
		revisa('Aún no hemos leído las bases: mira los requisitos en la convocatoria');
	} else {
		const k = ia.condiciones;
		if (p.forma === 'autonomo') {
			if (k.autonomos === 'no') no('No admite autónomos');
			else if (k.autonomos === 'si') bien('Admite autónomos');
		}
		if (p.forma === 'sociedad') {
			if (k.sociedades === 'no') no('No admite sociedades');
			else if (k.sociedades === 'si') bien('Admite sociedades');
		}
		if (p.forma === 'emprender') {
			if (k.emprendedores === 'si') bien('Sirve para quien aún no ha empezado');
			else if (k.emprendedores === 'no' || (k.antiguedadMinMeses ?? 0) > 0) no('Hay que tener ya el negocio en marcha');
			else revisa('No dice si sirve para quien aún no ha empezado');
		}

		if (k.maxEmpleados != null) {
			if (minEmp > k.maxEmpleados) no(`Solo hasta ${k.maxEmpleados} ${k.maxEmpleados === 1 ? 'trabajador' : 'trabajadores'}`);
			else if (maxEmp <= k.maxEmpleados) bien('Tu plantilla entra');
			else revisa(`Solo hasta ${k.maxEmpleados} trabajadores: comprueba tu plantilla`);
		}

		if (p.forma !== 'emprender') {
			const [minMes, maxMes] = MESES[p.antiguedad];
			if (k.antiguedadMinMeses != null && k.antiguedadMinMeses > 0) {
				if (maxMes < k.antiguedadMinMeses) no(`Pide al menos ${meses(k.antiguedadMinMeses)} de actividad`);
				else if (minMes >= k.antiguedadMinMeses) bien('Llevas el tiempo que pide');
				else revisa(`Pide al menos ${meses(k.antiguedadMinMeses)} de actividad`);
			}
			if (k.antiguedadMaxMeses != null) {
				if (minMes > k.antiguedadMaxMeses) no(`Solo para negocios de menos de ${meses(k.antiguedadMaxMeses)}`);
				else if (maxMes <= k.antiguedadMaxMeses) bien('Para negocios nuevos, como el tuyo');
				else revisa(`Solo para negocios de menos de ${meses(k.antiguedadMaxMeses)}`);
			}
		}
		if (ia.dudas) revisa(ia.dudas);
	}

	const estado: Estado = motivos.some((m) => m.tipo === 'no') ? 'no' : motivos.some((m) => m.tipo === 'revisa') ? 'revisa' : 'si';
	return { estado, motivos };
}

function meses(n: number): string {
	if (n % 12 === 0) return `${n / 12} ${n === 12 ? 'año' : 'años'}`;
	return `${n} ${n === 1 ? 'mes' : 'meses'}`;
}
