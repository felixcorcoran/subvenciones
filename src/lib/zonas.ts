/**
 * El nombre de una zona de la BDNS (código NUTS) bien escrito: la BDNS pone
 * las comunidades en mayúsculas y sin tildes («PAIS VASCO»).
 */
import provincias from '../data/provincias.json';

const nombres = new Map<string, string>([['ES', 'toda España']]);
for (const p of provincias) {
	nombres.set(p.codigo, p.nombre);
	nombres.set(p.codigoComunidad, p.comunidad);
}

export const nombreZona = (codigo: string, original: string) => nombres.get(codigo) ?? original;
