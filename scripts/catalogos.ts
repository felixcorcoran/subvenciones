/**
 * Las provincias del formulario, sacadas del árbol de regiones (códigos NUTS)
 * de la BDNS: así coinciden con las de cada convocatoria. Se ejecuta a mano
 * (npm run catalogos) y el resultado se sube: no cambia de un día para otro.
 */
import { writeFileSync } from 'node:fs';
import { getJson } from './bdns.ts';

interface Nodo {
	id: number;
	descripcion: string;
	children: Nodo[];
}

const arbol = await getJson<Nodo[]>('/regiones');
const espana = arbol.find((n) => n.descripcion.trim().startsWith('ES -'));
if (!espana) throw new Error('No está España en el árbol de regiones');

// La BDNS escribe las comunidades en mayúsculas y sin tildes: los nombres
// bien escritos, por su código NUTS 2
const comunidades: Record<string, string> = {
	ES11: 'Galicia', ES12: 'Asturias', ES13: 'Cantabria', ES21: 'País Vasco', ES22: 'Navarra', ES23: 'La Rioja',
	ES24: 'Aragón', ES30: 'Comunidad de Madrid', ES41: 'Castilla y León', ES42: 'Castilla-La Mancha', ES43: 'Extremadura',
	ES51: 'Cataluña', ES52: 'Comunitat Valenciana', ES53: 'Illes Balears', ES61: 'Andalucía', ES62: 'Región de Murcia',
	ES63: 'Ceuta', ES64: 'Melilla', ES70: 'Canarias',
};

const provincias: { codigo: string; nombre: string; comunidad: string; codigoComunidad: string }[] = [];
for (const macro of espana.children) {
	for (const comunidad of macro.children) {
		const codigoComunidad = comunidad.descripcion.split(' - ')[0].trim();
		for (const prov of comunidad.children) {
			const [codigo, nombre] = prov.descripcion.split(' - ').map((s) => s.trim());
			const nombreComunidad = comunidades[codigoComunidad];
			if (!nombreComunidad) throw new Error(`Comunidad sin nombre: ${codigoComunidad}`);
			provincias.push({ codigo, nombre, comunidad: nombreComunidad, codigoComunidad });
		}
	}
}
provincias.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
writeFileSync(new URL('../src/data/provincias.json', import.meta.url), JSON.stringify(provincias, null, '\t') + '\n');
console.log(`${provincias.length} provincias`);
