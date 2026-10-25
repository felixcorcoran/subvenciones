/** Una convocatoria y un perfil de partida para los tests: cada test cambia solo lo que prueba */
import type { Analisis, ConvocatoriaWeb } from '../src/lib/tipos';
import type { Perfil } from '../src/lib/encaje';

export const HOY = '2026-10-25';

export function analisis(extra: Partial<Analisis> = {}, condiciones: Partial<Analisis['condiciones']> = {}): Analisis {
	return {
		categoria: 'ayuda',
		resumen: 'Ayudas para digitalizar el comercio local.',
		paraQuien: 'Comercios de Andalucía.',
		importe: 'Hasta 3.000 € por negocio',
		importeMaximo: 3000,
		plazo: 'Hasta el 31 de diciembre de 2026.',
		plazoFin: null,
		requisitos: ['Estar al corriente con Hacienda.'],
		gastos: ['Página web.'],
		dudas: null,
		...extra,
		condiciones: {
			autonomos: 'si',
			sociedades: 'si',
			emprendedores: 'no_consta',
			maxEmpleados: null,
			antiguedadMinMeses: null,
			antiguedadMaxMeses: null,
			municipio: null,
			...condiciones,
		},
	};
}

export function convocatoria(extra: Partial<ConvocatoriaWeb> = {}): ConvocatoriaWeb {
	return {
		codigo: '900001',
		titulo: 'Ayudas a la digitalización del comercio',
		organo: { nivel1: 'AUTONOMICA', nivel2: 'ANDALUCÍA', nivel3: 'CONSEJERÍA DE EMPLEO' },
		sede: null,
		publicada: '2026-10-01',
		tipo: 'Concurrencia competitiva - canónica',
		presupuesto: 100000,
		beneficiarios: ['PYME Y PERSONAS FÍSICAS QUE DESARROLLAN ACTIVIDAD ECONÓMICA'],
		sectores: [],
		regiones: [{ codigo: 'ES61', nombre: 'ANDALUCIA' }],
		finalidad: 'Comercio, Turismo y Pymes',
		inicio: null,
		fin: '2026-12-31',
		textoFin: null,
		bases: null,
		enlace: 'https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/900001',
		ia: analisis(),
		...extra,
	};
}

/** Autónomo de Málaga, solo, de 1 a 3 años, hostelería */
export function perfil(extra: Partial<Perfil> = {}): Perfil {
	return { provincia: 'ES617', municipio: '', forma: 'autonomo', empleados: 0, antiguedad: '1a3', sector: 'I', ...extra };
}
