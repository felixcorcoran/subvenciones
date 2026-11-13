/**
 * Para qué sirve cada ayuda, en las categorías que se le preguntan al negocio
 * («¿Para qué buscas la ayuda?»). Las pone la IA a partir de lo que dicen las
 * bases (`ia.finalidades`). Es solo para ordenar y señalar en la web: no
 * cambia el veredicto del encaje (encaje.ts, contrato con alertas).
 */
export const FINALIDADES = [
	{ clave: 'empezar', nombre: 'Empezar o darme de alta' },
	{ clave: 'contratar', nombre: 'Contratar personal' },
	{ clave: 'digitalizar', nombre: 'Digitalizar el negocio' },
	{ clave: 'invertir', nombre: 'Equipos, maquinaria u obras' },
	{ clave: 'local', nombre: 'Alquiler o reforma del local' },
	{ clave: 'energia', nombre: 'Ahorro de energía y sostenibilidad' },
	{ clave: 'innovar', nombre: 'Innovación e I+D' },
	{ clave: 'exportar', nombre: 'Ferias, exportación y promoción' },
	{ clave: 'formacion', nombre: 'Formación' },
	{ clave: 'liquidez', nombre: 'Gastos corrientes y liquidez' },
] as const;

export type Finalidad = (typeof FINALIDADES)[number]['clave'];

export const CLAVES_FINALIDAD = FINALIDADES.map((f) => f.clave) as Finalidad[];

export const esFinalidad = (v: unknown): v is Finalidad => typeof v === 'string' && (CLAVES_FINALIDAD as string[]).includes(v);

export const nombreFinalidad = (clave: string) => FINALIDADES.find((f) => f.clave === clave)?.nombre ?? clave;

/** Solo las claves conocidas, sin repetir; vacío si no hay ninguna */
export const limpiarFinalidades = (v: unknown): Finalidad[] => (Array.isArray(v) ? [...new Set(v.filter(esFinalidad))] : []);

/** Si la ayuda sirve para lo que busca el negocio (sin finalidad elegida, no se señala nada) */
export const sirvePara = (finalidades: string[] | undefined, buscada: string | null | undefined) => Boolean(buscada && finalidades?.includes(buscada));
