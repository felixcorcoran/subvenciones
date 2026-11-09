// Convierte datos/convocatorias.json de la web en eventos «convocatoria.leida», uno por línea:
// «código<TAB>evento JSON». Sirve para cargar Kafka con datos reales mientras no exista el
// servicio de recogida.
//
//   node herramientas/eventos-convocatorias.mjs ../datos/convocatorias.json \
//     | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
//         --bootstrap-server localhost:29092 --topic convocatorias.leidas \
//         --property parse.key=true --property key.separator=$'\t'
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

const ruta = process.argv[2] ?? '../datos/convocatorias.json';
const { convocatorias } = JSON.parse(readFileSync(ruta, 'utf8'));
const ahora = new Date().toISOString();

for (const convocatoria of convocatorias) {
	const evento = { id: randomUUID(), tipo: 'convocatoria.leida', version: 1, ocurrido: ahora, convocatoria };
	// JSON.stringify escapa los tabuladores del texto: el único tabulador de la línea es el separador
	process.stdout.write(`${convocatoria.codigo}\t${JSON.stringify(evento)}\n`);
}
process.stderr.write(`${convocatorias.length} eventos\n`);
