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
	enlace: string;
	ia: Analisis | null;
	iaModelo?: string;
	/** Si no se pudo leer el documento, por qué (se reintenta otro día) */
	iaError?: string;
	iaIntentos?: number;
}

/** Lo que llega a la página (src/pages/convocatorias.json.ts) */
export type ConvocatoriaWeb = Omit<Convocatoria, 'documento' | 'iaModelo' | 'iaError' | 'iaIntentos' | 'textoInicio'>;

export interface Datos {
	actualizado: string;
	convocatorias: Convocatoria[];
}
