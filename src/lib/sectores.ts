/**
 * Las secciones de la CNAE-2025 (NACE Rev. 2.1), las mismas 22 que usa la
 * BDNS en /api/actividades. Cada convocatoria indica su sector con la letra
 * de la sección («G COMERCIO…») o con una división de dos cifras o más
 * («47 Comercio al por menor», «94.1 …»): las divisiones se llevan a su
 * sección por los rangos de la clasificación.
 */
export const SECCIONES: { letra: string; nombre: string; desde: number; hasta: number }[] = [
	{ letra: 'A', nombre: 'Agricultura, ganadería, silvicultura y pesca', desde: 1, hasta: 3 },
	{ letra: 'B', nombre: 'Industrias extractivas', desde: 5, hasta: 9 },
	{ letra: 'C', nombre: 'Industria manufacturera', desde: 10, hasta: 33 },
	{ letra: 'D', nombre: 'Energía eléctrica, gas y climatización', desde: 35, hasta: 35 },
	{ letra: 'E', nombre: 'Agua, saneamiento y residuos', desde: 36, hasta: 39 },
	{ letra: 'F', nombre: 'Construcción', desde: 41, hasta: 43 },
	{ letra: 'G', nombre: 'Comercio al por mayor y al por menor', desde: 46, hasta: 47 },
	{ letra: 'H', nombre: 'Transporte y almacenamiento', desde: 49, hasta: 53 },
	{ letra: 'I', nombre: 'Hostelería', desde: 55, hasta: 56 },
	{ letra: 'J', nombre: 'Edición, radio, televisión y contenidos', desde: 58, hasta: 60 },
	{ letra: 'K', nombre: 'Telecomunicaciones, informática y servicios de información', desde: 61, hasta: 63 },
	{ letra: 'L', nombre: 'Actividades financieras y de seguros', desde: 64, hasta: 66 },
	{ letra: 'M', nombre: 'Actividades inmobiliarias', desde: 68, hasta: 68 },
	{ letra: 'N', nombre: 'Actividades profesionales, científicas y técnicas', desde: 69, hasta: 75 },
	{ letra: 'O', nombre: 'Actividades administrativas y servicios auxiliares', desde: 77, hasta: 82 },
	{ letra: 'P', nombre: 'Administración pública y defensa', desde: 84, hasta: 84 },
	{ letra: 'Q', nombre: 'Educación', desde: 85, hasta: 85 },
	{ letra: 'R', nombre: 'Actividades sanitarias y de servicios sociales', desde: 86, hasta: 88 },
	{ letra: 'S', nombre: 'Actividades artísticas, deportivas y de entretenimiento', desde: 90, hasta: 93 },
	{ letra: 'T', nombre: 'Otros servicios (peluquería, estética, reparaciones…)', desde: 94, hasta: 96 },
	{ letra: 'U', nombre: 'Hogares como empleadores', desde: 97, hasta: 98 },
	{ letra: 'V', nombre: 'Organismos extraterritoriales', desde: 99, hasta: 99 },
];

/** La sección (letra) de un código de sector de la BDNS, o null si no se reconoce */
export function seccionDe(codigo: string): string | null {
	const c = codigo.trim().toUpperCase();
	if (/^[A-V]$/.test(c)) return c;
	const division = Number.parseInt(c, 10);
	if (Number.isNaN(division)) return null;
	return SECCIONES.find((s) => division >= s.desde && division <= s.hasta)?.letra ?? null;
}

export const nombreSeccion = (letra: string) => SECCIONES.find((s) => s.letra === letra)?.nombre ?? letra;
