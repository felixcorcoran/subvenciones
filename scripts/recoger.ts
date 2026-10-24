/**
 * Cada mañana: las convocatorias nuevas de toda España para empresas
 * (pymes, autónomos y grandes empresas), con su ficha completa.
 *
 * Se quedan las que una empresa puede pedir. Se descartan las de concesión
 * directa (van a una entidad concreta: un convenio, una subvención
 * nominativa…) y las que ya han cerrado. También se retiran las que cierran
 * con el tiempo.
 *
 *   DIAS_ATRAS  días hacia atrás en la primera pasada (por defecto 14)
 */
import { buscarNuevas, enlaceFicha, ficha, type Ficha } from './bdns.ts';
import { guardarDatos, guardarVistas, hoyIso, leerDatos, leerVistas } from './almacen.ts';
import type { Convocatoria } from '../src/lib/tipos.ts';

const hoy = hoyIso();
const datos = leerDatos();
const vistas = leerVistas();
const guardadas = new Map(datos.convocatorias.map((c) => [c.codigo, c]));

// Desde la última pasada (con unos días de margen: hay convocatorias que se
// registran con fecha de días anteriores), o DIAS_ATRAS la primera vez
const diasAtras = Number(process.env.DIAS_ATRAS ?? 14);
const desde = new Date();
if (datos.actualizado) {
	const ultima = new Date(datos.actualizado);
	ultima.setDate(ultima.getDate() - 4);
	desde.setTime(Math.max(ultima.getTime(), Date.now() - 30 * 86_400_000));
} else {
	desde.setDate(desde.getDate() - diasAtras);
}

const encontradas = await buscarNuevas(desde, new Date());
const nuevas = encontradas.filter((r) => !guardadas.has(r.numeroConvocatoria) && !vistas[r.numeroConvocatoria]);
console.log(`${encontradas.length} convocatorias para empresas desde ${desde.toISOString().slice(0, 10)}; ${nuevas.length} sin ver`);

const descartar = (codigo: string, motivo: string) => (vistas[codigo] = { motivo, fecha: hoy });

const iso = (s: string | null) => (s && /^\d{4}-\d{2}-\d{2}/.test(s) ? s.slice(0, 10) : null);
const limpio = (s: string | null) => (s?.trim() ? s.trim() : null);

function aConvocatoria(f: Ficha): Convocatoria {
	const doc = f.documentos.find((d) => /español|castellano/i.test(d.descripcion)) ?? f.documentos[0] ?? null;
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
		documento: doc ? { id: doc.id, nombre: doc.nombreFic } : null,
		enlace: enlaceFicha(f.codigoBDNS),
		ia: null,
	};
}

let anadidas = 0;
for (const r of nuevas) {
	let f: Ficha;
	try {
		f = await ficha(r.numeroConvocatoria);
	} catch (err) {
		console.warn(`  ${r.numeroConvocatoria}: sin ficha (${(err as Error).message}); se reintenta mañana`);
		continue;
	}
	if (/^concesi[oó]n directa/i.test(f.tipoConvocatoria ?? '')) {
		descartar(f.codigoBDNS, 'concesión directa');
		continue;
	}
	const c = aConvocatoria(f);
	if (c.fin && c.fin < hoy) {
		descartar(c.codigo, 'cerrada');
		continue;
	}
	guardadas.set(c.codigo, c);
	anadidas++;
}

// Fuera las que ya han cerrado, y las que llevan cuatro meses sin fecha de
// cierre conocida (casi siempre ya no están abiertas)
const limite = new Date(Date.now() - 120 * 86_400_000).toISOString().slice(0, 10);
let retiradas = 0;
for (const [codigo, c] of guardadas) {
	const fin = c.ia?.plazoFin ?? c.fin;
	if ((fin && fin < hoy) || (!fin && c.publicada < limite)) {
		guardadas.delete(codigo);
		descartar(codigo, 'cerrada');
		retiradas++;
	}
}

// Las descartadas solo hacen falta mientras puedan volver a salir en la búsqueda
const corte = new Date(Date.now() - 45 * 86_400_000).toISOString().slice(0, 10);
for (const [codigo, v] of Object.entries(vistas)) if (v.fecha < corte) delete vistas[codigo];

datos.actualizado = new Date().toISOString();
datos.convocatorias = [...guardadas.values()];
guardarDatos(datos);
guardarVistas(vistas);
console.log(`${anadidas} añadidas, ${retiradas} retiradas; ${datos.convocatorias.length} abiertas en total`);
