/**
 * De la ficha de la BDNS a una convocatoria guardada, y la actualización de
 * las que ya están guardadas cuando se vuelve a pedir su ficha.
 *
 * Al actualizar no se pierde nada: si la fuente deja de dar un dato que antes
 * daba, se conserva el anterior (una ficha incompleta no borra información).
 * Lo que cambia se apunta en `cambios`.
 */
import { enlaceFicha, type Ficha } from './bdns.ts';
import type { Convocatoria } from '../src/lib/tipos.ts';

const iso = (s: string | null) => (s && /^\d{4}-\d{2}-\d{2}/.test(s) ? s.slice(0, 10) : null);
const limpio = (s: string | null) => (s?.trim() ? s.trim() : null);

/** Una ficha con lo mínimo para fiarse de ella; si no, se trata como un fallo de consulta */
export function fichaValida(f: Ficha | null | undefined, codigo: string): f is Ficha {
	return Boolean(
		f &&
			f.codigoBDNS === codigo &&
			f.descripcion?.trim() &&
			/^\d{4}-\d{2}-\d{2}/.test(f.fechaRecepcion ?? '') &&
			f.organo &&
			Array.isArray(f.documentos) &&
			Array.isArray(f.tiposBeneficiarios) &&
			Array.isArray(f.sectores) &&
			Array.isArray(f.regiones),
	);
}

/** El documento de las bases: el que ya se usaba si sigue en la ficha; si no, el que está en castellano o el primero */
function elegirDocumento(f: Ficha, anterior: Convocatoria['documento'] = null): Convocatoria['documento'] {
	const sigue = anterior && f.documentos.find((d) => d.id === anterior.id);
	const doc = sigue ?? f.documentos.find((d) => /español|castellano/i.test(d.descripcion)) ?? f.documentos[0] ?? null;
	return doc ? { id: doc.id, nombre: doc.nombreFic } : null;
}

export function aConvocatoria(f: Ficha, hoy: string): Convocatoria {
	return {
		codigo: f.codigoBDNS,
		titulo: f.descripcion.trim(),
		organo: f.organo,
		sede: limpio(f.sedeElectronica),
		publicada: f.fechaRecepcion,
		tipo: limpio(f.tipoConvocatoria),
		presupuesto: f.presupuestoTotal ?? null,
		beneficiarios: f.tiposBeneficiarios.map((b) => b.descripcion.trim()),
		sectores: f.sectores.map((s) => ({ codigo: s.codigo.trim(), nombre: s.descripcion.trim() })),
		regiones: f.regiones.map((r) => {
			const [codigo, ...resto] = r.descripcion.split(' - ');
			return { codigo: codigo.trim(), nombre: resto.join(' - ').trim() };
		}),
		finalidad: limpio(f.descripcionFinalidad),
		inicio: iso(f.fechaInicioSolicitud),
		fin: iso(f.fechaFinSolicitud),
		textoInicio: limpio(f.textInicio),
		textoFin: limpio(f.textFin),
		bases: limpio(f.urlBasesReguladoras),
		documento: elegirDocumento(f),
		docs: f.documentos.map((d) => d.id).sort((a, b) => a - b),
		enlace: enlaceFicha(f.codigoBDNS),
		ia: null,
		comprobada: hoy,
	};
}

/** Los datos de la ficha que se comparan al actualizar */
const CAMPOS = ['titulo', 'sede', 'tipo', 'presupuesto', 'beneficiarios', 'sectores', 'regiones', 'finalidad', 'inicio', 'fin', 'textoInicio', 'textoFin', 'bases'] as const;
const PLAZO = new Set<string>(['inicio', 'fin', 'textoInicio', 'textoFin']);
/** Cuántos cambios se guardan de cada convocatoria */
const MAX_CAMBIOS = 10;

const vacio = (v: unknown) => v === null || v === undefined || (Array.isArray(v) && v.length === 0);
const igual = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

export interface Actualizacion {
	convocatoria: Convocatoria;
	/** Los campos que han cambiado (vacío si nada) */
	campos: string[];
	/** Datos que la ficha ya no trae y se conservan */
	conservados: string[];
}

/**
 * La convocatoria guardada con lo que dice su ficha de hoy. No modifica la
 * guardada: devuelve una nueva.
 *
 * - Un campo que la ficha trae vacío y antes tenía valor se conserva.
 * - Si cambia el documento de las bases, o el plazo, la IA lo vuelve a leer
 *   (`iaRevisar`); mientras, se mantiene lo leído antes. Que la fecha de la
 *   BDNS mande sobre la de la IA lo hace después revisarCierreIa (lectura.ts).
 */
export function actualizar(guardada: Convocatoria, f: Ficha, hoy: string): Actualizacion {
	const nueva = aConvocatoria(f, hoy);
	const c: Convocatoria = structuredClone(guardada);
	const campos: string[] = [];
	const conservados: string[] = [];

	for (const campo of CAMPOS) {
		const antes = guardada[campo];
		const ahora = nueva[campo];
		if (igual(antes, ahora)) continue;
		if (vacio(ahora) && !vacio(antes)) {
			conservados.push(campo);
			continue;
		}
		(c as unknown as Record<string, unknown>)[campo] = ahora;
		campos.push(campo);
	}

	const documento = elegirDocumento(f, guardada.documento);
	if (documento && documento.id !== guardada.documento?.id) {
		c.documento = documento;
		campos.push('documento');
	} else if (!documento && guardada.documento) conservados.push('documento');

	// Los documentos que cuelgan de la ficha (correcciones, extractos…). La
	// primera vez solo se apuntan: no hay con qué comparar
	if (nueva.docs!.length > 0) {
		if (guardada.docs && !igual(guardada.docs, nueva.docs)) campos.push('documentos');
		c.docs = nueva.docs;
	}

	if (c.ia) {
		const motivo = campos.includes('documento') ? 'documento nuevo' : campos.some((k) => PLAZO.has(k)) ? 'cambió el plazo' : null;
		if (motivo) {
			c.iaRevisar = motivo;
			delete c.iaIntentos;
			delete c.iaError;
			delete c.iaIntentadoEl;
		}
	} else if (campos.includes('documento')) {
		// Un documento nuevo merece otra oportunidad aunque el anterior fallara
		delete c.iaIntentos;
		delete c.iaError;
		delete c.iaIntentadoEl;
	}

	if (campos.length > 0) c.cambios = [...(guardada.cambios ?? []), { fecha: hoy, campos }].slice(-MAX_CAMBIOS);
	c.comprobada = hoy;
	return { convocatoria: c, campos, conservados };
}
