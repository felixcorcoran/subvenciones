/**
 * Cómo lee Gemini las bases, medido. Dos fuentes de verdad:
 *
 * - La BDNS, para lo que da estructurado y no depende de la IA: la fecha de
 *   cierre de su ficha y el municipio del ayuntamiento que convoca. Vale para
 *   todas las convocatorias, sin anotar nada.
 * - Casos anotados a mano (evaluacion/anotados.json), leyendo las bases: para
 *   lo demás (categoría, autónomos, plantilla…). Se van añadiendo.
 *
 * Cada campo se cuenta como acierto, «no consta» (la IA no lo da: no es un
 * error, la web lo trata como desconocido) o error.
 */
import type { Analisis, Convocatoria } from '../src/lib/tipos.ts';
import { municipioDe, normalizarMunicipio } from '../src/lib/encaje.ts';

/** Lo correcto de una convocatoria, anotado a mano leyendo sus bases */
export interface Anotado {
	codigo: string;
	/** Quién lo anotó y cuándo, para poder revisarlo */
	nota?: string;
	categoria?: Analisis['categoria'];
	plazoFin?: string | null;
	autonomos?: 'si' | 'no' | 'no_consta';
	sociedades?: 'si' | 'no' | 'no_consta';
	emprendedores?: 'si' | 'no' | 'no_consta';
	maxEmpleados?: number | null;
	antiguedadMinMeses?: number | null;
	municipio?: string | null;
	/** Finalidades que tiene que incluir (puede dar más) */
	finalidades?: string[];
}

export type Resultado = 'acierto' | 'no_consta' | 'error';
export interface Comparacion {
	codigo: string;
	campo: string;
	fuente: 'bdns' | 'anotado';
	resultado: Resultado;
	esperado: unknown;
	obtenido: unknown;
}

const vacio = (v: unknown) => v === null || v === undefined || v === 'no_consta';

/** Lo que dice la BDNS y sirve para juzgar a la IA */
export function verdadBdns(c: Convocatoria): Partial<Pick<Anotado, 'plazoFin' | 'municipio'>> {
	const v: Partial<Pick<Anotado, 'plazoFin' | 'municipio'>> = {};
	if (c.fin) v.plazoFin = c.fin;
	// El de su ayuntamiento (no lo que haya leído la IA, que es lo que se juzga)
	if (c.organo.nivel1 === 'LOCAL' && /^AYUNTAMIENTO|^AJUNTAMENT|^CONCELLO|^UDALA/i.test(c.organo.nivel3)) {
		v.municipio = municipioDe({ ...c, ia: null });
	}
	return v;
}

function juzgar(campo: string, esperado: unknown, obtenido: unknown): Resultado {
	if (campo === 'municipio' && typeof esperado === 'string') {
		if (vacio(obtenido)) return 'no_consta';
		// Los nombres bilingües de la BDNS («Elche/Elx»): vale cualquiera de los dos
		const nombres = esperado.split('/').map(normalizarMunicipio).filter(Boolean);
		const b = normalizarMunicipio(String(obtenido));
		return nombres.some((a) => a === b || b.includes(a)) ? 'acierto' : 'error';
	}
	if (campo === 'finalidades' && Array.isArray(esperado)) {
		const dadas = Array.isArray(obtenido) ? obtenido : [];
		if (dadas.length === 0 && esperado.length > 0) return 'no_consta';
		return esperado.every((f) => dadas.includes(f)) ? 'acierto' : 'error';
	}
	if (vacio(esperado)) return vacio(obtenido) ? 'acierto' : 'error';
	if (vacio(obtenido)) return 'no_consta';
	return esperado === obtenido ? 'acierto' : 'error';
}

/** Compara una lectura con la BDNS y, si la hay, con su anotación */
export function comparar(c: Convocatoria, ia: Analisis, anotado?: Anotado): Comparacion[] {
	const obtenido: Record<string, unknown> = {
		categoria: ia.categoria,
		plazoFin: ia.plazoFin,
		autonomos: ia.condiciones.autonomos,
		sociedades: ia.condiciones.sociedades,
		emprendedores: ia.condiciones.emprendedores,
		maxEmpleados: ia.condiciones.maxEmpleados,
		antiguedadMinMeses: ia.condiciones.antiguedadMinMeses,
		municipio: ia.condiciones.municipio,
		finalidades: ia.finalidades,
	};
	const fila = (campo: string, fuente: Comparacion['fuente'], esperado: unknown): Comparacion => ({
		codigo: c.codigo,
		campo,
		fuente,
		resultado: juzgar(campo, esperado, obtenido[campo]),
		esperado,
		obtenido: obtenido[campo],
	});
	const filas: Comparacion[] = [];
	for (const [campo, esperado] of Object.entries(verdadBdns(c))) {
		// Si hay anotación de ese campo, manda la anotación
		if (anotado && campo in anotado) continue;
		// Una fecha de las bases anterior al inicio de la BDNS puede ser un plazo previo legítimo (fechas.ts)
		if (campo === 'plazoFin' && ia.plazoFin && c.inicio && ia.plazoFin < c.inicio) continue;
		filas.push(fila(campo, 'bdns', esperado));
	}
	if (anotado) {
		for (const [campo, esperado] of Object.entries(anotado)) {
			if (campo === 'codigo' || campo === 'nota') continue;
			filas.push(fila(campo, 'anotado', esperado));
		}
	}
	return filas;
}

/** El informe en Markdown: por campo, aciertos, «no consta» y errores; y los errores, uno a uno */
export function informe(titulo: string, filas: Comparacion[], maxErrores = 40): string {
	const campos = [...new Set(filas.map((f) => `${f.campo} (${f.fuente === 'bdns' ? 'BDNS' : 'anotado'})`))].sort();
	const lineas = [`## ${titulo}`, '', '| Campo | Casos | Aciertos | No consta | Errores | Acierto |', '| --- | ---: | ---: | ---: | ---: | ---: |'];
	for (const campo of campos) {
		const suyas = filas.filter((f) => `${f.campo} (${f.fuente === 'bdns' ? 'BDNS' : 'anotado'})` === campo);
		const n = (r: Resultado) => suyas.filter((f) => f.resultado === r).length;
		const juzgadas = n('acierto') + n('error');
		const pct = juzgadas ? `${Math.round((n('acierto') / juzgadas) * 100)} %` : '—';
		lineas.push(`| ${campo} | ${suyas.length} | ${n('acierto')} | ${n('no_consta')} | ${n('error')} | ${pct} |`);
	}
	const errores = filas.filter((f) => f.resultado === 'error');
	lineas.push('', `«Acierto» cuenta solo las que la IA contestó: «no consta» no es un error.`, '');
	if (errores.length) {
		lineas.push(`### Errores${errores.length > maxErrores ? ` (los ${maxErrores} primeros de ${errores.length})` : ''}`, '');
		for (const e of errores.slice(0, maxErrores))
			lineas.push(`- ${e.codigo} · ${e.campo}: esperado ${JSON.stringify(e.esperado)}, la IA dio ${JSON.stringify(e.obtenido)}`);
	} else lineas.push('Sin errores.');
	return lineas.join('\n') + '\n';
}
