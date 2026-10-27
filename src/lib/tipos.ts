/** Lo que la IA saca de las bases de cada convocatoria (scripts/analizar.ts) */
export interface Analisis {
	/**
	 * ayuda: dinero (o ventaja) que un negocio puede pedir para su actividad.
	 * premio: concursos y premios (escaparates, fotografía…).
	 * otro: selección de entidades colaboradoras, becas a personas…
	 */
	categoria: 'ayuda' | 'premio' | 'otro';
	/** Qué financia, en una o dos frases claras */
	resumen: string;
	/** A quién va dirigida, dicho como lo entendería el dueño de un negocio */
	paraQuien: string;
	/** «Hasta 3.000 € por negocio», «El 50 % del gasto»… o «No lo indica» */
	importe: string;
	importeMaximo: number | null;
	/** El plazo dicho con claridad */
	plazo: string;
	/** AAAA-MM-DD, solo si se puede saber con certeza */
	plazoFin: string | null;
	/** Cada requisito, breve y concreto */
	requisitos: string[];
	/** En qué se puede gastar la ayuda */
	gastos: string[];
	condiciones: Condiciones;
	/** Lo que no queda claro en las bases */
	dudas: string | null;
}

export type SiNo = 'si' | 'no' | 'no_consta';

export interface Condiciones {
	autonomos: SiNo;
	sociedades: SiNo;
	/** Pueden pedirla quienes aún no han empezado su negocio */
	emprendedores: SiNo;
	maxEmpleados: number | null;
	/** El negocio tiene que llevar al menos estos meses en marcha */
	antiguedadMinMeses: number | null;
	/** Solo negocios con menos de estos meses de vida */
	antiguedadMaxMeses: number | null;
	/** Si exige estar en un municipio concreto */
	municipio: string | null;
}

export interface Convocatoria {
	codigo: string;
	titulo: string;
	organo: { nivel1: string; nivel2: string; nivel3: string };
	sede: string | null;
	/** Fecha en que se registró en la BDNS (AAAA-MM-DD) */
	publicada: string;
	tipo: string | null;
	presupuesto: number | null;
	beneficiarios: string[];
	sectores: { codigo: string; nombre: string }[];
	regiones: { codigo: string; nombre: string }[];
	finalidad: string | null;
	inicio: string | null;
	fin: string | null;
	textoInicio: string | null;
	textoFin: string | null;
	bases: string | null;
	documento: { id: number; nombre: string } | null;
	/** Ids de todos los documentos de la ficha, para notar cuándo se añade o quita alguno */
	docs?: number[];
	enlace: string;
	/** Último día (AAAA-MM-DD) en que se leyó bien su ficha en la BDNS */
	comprobada?: string;
	/** Lo que ha cambiado en la ficha desde que se guardó (los últimos) */
	cambios?: { fecha: string; campos: string[] }[];
	ia: Analisis | null;
	iaModelo?: string;
	/** La IA tiene que volver a leerla (documento nuevo o plazo cambiado); lo leído antes se mantiene mientras */
	iaRevisar?: string;
	/** Si no se pudo leer el documento, por qué (se reintenta otro día) */
	iaError?: string;
	iaIntentos?: number;
	/** Día del último intento fallido de la IA */
	iaIntentadoEl?: string;
	/**
	 * Fecha de cierre que la IA sacó de las bases y no se usa (contradice a la
	 * BDNS o es dudosa: scripts/lectura.ts). En `ia.plazoFin` solo queda una
	 * fecha de las bases que se pueda usar.
	 */
	cierreDescartado?: { fecha: string; motivo: string };
}

/** Lo que llega a la página (src/pages/convocatorias.json.ts) */
export type ConvocatoriaWeb = Omit<Convocatoria, 'documento' | 'docs' | 'iaModelo' | 'iaError' | 'iaIntentos' | 'iaIntentadoEl' | 'textoInicio' | 'cierreDescartado'>;

/** Cómo fue la última pasada de cada parte, para la vigilancia (scripts/salud.ts) */
export interface Salud {
	recogida?: {
		fecha: string;
		/** Convocatorias nuevas sin ficha (se reintentan) */
		nuevasSinFicha: number;
		fichasComprobadas: number;
		fichasFallidas: number;
	};
	lectura?: {
		fecha: string;
		pendientes: number;
		leidas: number;
		fallidas: number;
		/** Día de la última lectura correcta */
		ultimaLectura: string | null;
	};
}

export interface Datos {
	actualizado: string;
	convocatorias: Convocatoria[];
	salud?: Salud;
}
