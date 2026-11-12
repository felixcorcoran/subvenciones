/**
 * La API pública del Sistema Nacional de Publicidad de Subvenciones (BDNS):
 * https://www.infosubvenciones.es/bdnstrans/api. Sin clave. Su aviso legal
 * permite reutilizar los datos con fines comerciales y no comerciales, y avisa
 * de que puede limitar el acceso si se abusa: por eso una sola pasada al día,
 * de una en una y con pausa entre peticiones.
 */

const BASE = 'https://www.infosubvenciones.es/bdnstrans/api';
const PAUSA_MS = 400;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
let ultima = 0;

async function pedir(ruta: string): Promise<Response> {
	const espera = ultima + PAUSA_MS - Date.now();
	if (espera > 0) await sleep(espera);
	ultima = Date.now();
	for (let intento = 1; ; intento++) {
		try {
			const res = await fetch(`${BASE}${ruta}`, {
				signal: AbortSignal.timeout(30_000),
				headers: { accept: 'application/json', 'user-agent': 'subvenciones.felixcorcoran.es (radar de ayudas para pymes)' },
			});
			if (res.ok || res.status < 500 || intento === 3) return res;
		} catch (err) {
			if (intento === 3) throw err;
		}
		await sleep(5_000 * intento);
	}
}

export async function getJson<T>(ruta: string): Promise<T> {
	const res = await pedir(ruta);
	if (!res.ok) throw new Error(`BDNS ${res.status} en ${ruta}: ${(await res.text()).slice(0, 300)}`);
	return (await res.json()) as T;
}

export async function getBytes(ruta: string): Promise<Buffer> {
	const res = await pedir(ruta);
	if (!res.ok) throw new Error(`BDNS ${res.status} en ${ruta}`);
	return Buffer.from(await res.arrayBuffer());
}

/** dd/mm/aaaa, el formato que pide la búsqueda */
export const fechaBdns = (d: Date) =>
	`${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;

/**
 * Días de margen antes de la última pasada. La búsqueda filtra por la fecha de
 * registro (fechaRecepcion), pero una convocatoria solo aparece cuando se
 * publica, a veces una semana o más después: 938128 se registró el 27/10 y no
 * salió hasta el 3/11. Con 4 días de margen se perdían.
 */
export const MARGEN_DIAS = 14;
const DIA_MS = 86_400_000;

/**
 * Desde qué fecha buscar: la última pasada menos el margen (como mucho 30 días
 * atrás) o, la primera vez, `diasAtras` días.
 */
export function inicioBusqueda(actualizado: string | undefined, ahora: Date, diasAtras: number): Date {
	if (!actualizado) return new Date(ahora.getTime() - diasAtras * DIA_MS);
	const conMargen = new Date(actualizado).getTime() - MARGEN_DIAS * DIA_MS;
	return new Date(Math.max(conMargen, ahora.getTime() - 30 * DIA_MS));
}

const isoDia = (d: Date) => d.toISOString().slice(0, 10);
const menosDias = (iso: string, dias: number) => isoDia(new Date(Date.parse(`${iso}T00:00:00Z`) - dias * DIA_MS));

/**
 * Carga de las convocatorias anteriores al arranque del radar, poco a poco:
 * la búsqueda diaria solo trae las nuevas, y las que se registraron antes y
 * siguen abiertas no aparecían nunca. Cada pasada busca `tramo` días más
 * atrás de lo ya buscado, hasta `dias` antes del arranque. Devuelve el tramo
 * que toca (fechas AAAA-MM-DD, ambas incluidas) y cómo queda el relleno, o
 * null si ya está completo.
 *
 * `masAntigua` es la primera fecha que cubrió la búsqueda normal (la
 * convocatoria guardada más antigua), para empezar justo antes.
 */
export function tramoRelleno(
	relleno: { desde: string; limite: string } | undefined,
	hoy: string,
	masAntigua: string,
	dias: number,
	tramo: number,
): { desde: string; hasta: string; siguiente: { desde: string; limite: string } } | null {
	const actual = relleno ?? { desde: masAntigua, limite: menosDias(hoy, dias) };
	if (actual.desde <= actual.limite) return null;
	const hasta = menosDias(actual.desde, 1);
	const desde = [menosDias(actual.desde, tramo), actual.limite].sort().at(-1)!;
	return { desde, hasta, siguiente: { desde, limite: actual.limite } };
}

export interface ResultadoBusqueda {
	id: number;
	numeroConvocatoria: string;
	descripcion: string;
	fechaRecepcion: string;
	nivel1: string;
	nivel2: string;
	nivel3: string;
}

interface PaginaBusqueda {
	content: ResultadoBusqueda[];
	last: boolean;
	totalElements: number;
}

/**
 * Convocatorias registradas entre dos fechas para empresas: tipo de
 * beneficiario 3 (pyme y personas físicas con actividad económica, que
 * incluye a los autónomos) y 4 (gran empresa).
 */
export async function buscarNuevas(desde: Date, hasta: Date): Promise<ResultadoBusqueda[]> {
	const todas: ResultadoBusqueda[] = [];
	for (let page = 0; ; page++) {
		const q = new URLSearchParams({
			page: String(page),
			pageSize: '100',
			fechaDesde: fechaBdns(desde),
			fechaHasta: fechaBdns(hasta),
			tiposBeneficiario: '3,4',
		});
		const data = await getJson<PaginaBusqueda>(`/convocatorias/busqueda?${q}`);
		todas.push(...data.content);
		if (data.last || data.content.length === 0) break;
	}
	return todas;
}

export interface Ficha {
	id: number;
	codigoBDNS: string;
	organo: { nivel1: string; nivel2: string; nivel3: string };
	sedeElectronica: string | null;
	fechaRecepcion: string;
	tipoConvocatoria: string | null;
	presupuestoTotal: number | null;
	descripcion: string;
	tiposBeneficiarios: { descripcion: string }[];
	sectores: { descripcion: string; codigo: string }[];
	regiones: { descripcion: string }[];
	descripcionFinalidad: string | null;
	urlBasesReguladoras: string | null;
	fechaInicioSolicitud: string | null;
	fechaFinSolicitud: string | null;
	textInicio: string | null;
	textFin: string | null;
	documentos: { id: number; descripcion: string; nombreFic: string; long: number }[];
}

export const ficha = (codigo: string) => getJson<Ficha>(`/convocatorias?numConv=${encodeURIComponent(codigo)}`);

export const documento = (id: number) => getBytes(`/convocatorias/documentos?idDocumento=${id}`);

/** La ficha pública de la convocatoria en infosubvenciones.es */
export const enlaceFicha = (codigo: string) => `https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/${codigo}`;
