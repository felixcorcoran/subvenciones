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
