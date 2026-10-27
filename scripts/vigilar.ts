/**
 * Para la Action de vigilancia: escribe el diagnóstico en informe.md y deja
 * `problemas=true|false` en la salida del paso. La Action abre o cierra un
 * único issue con eso.
 */
import { appendFileSync, writeFileSync } from 'node:fs';
import { leerDatos } from './almacen.ts';
import { diagnostico } from './salud.ts';

const problemas: { tipo: string; texto: string }[] = diagnostico(leerDatos());
// Lanzamiento manual con «prueba»: comprueba que la Action puede abrir y cerrar el issue
if (process.env.PRUEBA === 'true') problemas.push({ tipo: 'prueba', texto: 'Aviso de prueba lanzado a mano; no es un fallo real. Se cierra con el siguiente lanzamiento sin «prueba».' });
const informe = problemas.length
	? `La recogida diaria del radar necesita atención:\n\n${problemas.map((p) => `- **${p.tipo}**: ${p.texto}`).join('\n')}\n\nEste aviso se cierra solo cuando la vigilancia vuelva a ver todo en orden.\n`
	: 'Todo en orden.\n';
writeFileSync('informe.md', informe);
console.log(informe);
if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `problemas=${problemas.length > 0}\n`);
