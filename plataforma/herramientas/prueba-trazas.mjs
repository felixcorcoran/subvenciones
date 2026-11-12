// Prueba a mano de las trazas contra docker compose: lanza una recogida por la pasarela y busca
// su traza en Tempo (por Grafana). No imprime tokens ni secretos.
//   node herramientas/prueba-trazas.mjs infra/keycloak/realm-subvenciones.json
import { readFileSync } from 'node:fs';

const realm = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const espera = (ms) => new Promise((r) => setTimeout(r, ms));

async function listo(url) {
	for (let i = 0; i < 120; i++) {
		try {
			if ((await fetch(url)).ok) return;
		} catch {}
		await espera(2000);
	}
	throw new Error('No responde: ' + url);
}

await listo('http://localhost:8180/realms/subvenciones');
for (const p of [8000, 8081, 8082]) await listo(`http://localhost:${p}/actuator/health`);
await listo('http://localhost:3000/api/health');

const r = await fetch('http://localhost:8180/realms/subvenciones/protocol/openid-connect/token', {
	method: 'POST',
	headers: { 'content-type': 'application/x-www-form-urlencoded' },
	body: new URLSearchParams({
		grant_type: 'client_credentials',
		client_id: 'operaciones',
		client_secret: realm.clients.find((c) => c.clientId === 'operaciones').secret,
	}),
});
const token = (await r.json()).access_token;

const recogida = await fetch('http://localhost:8000/api/v1/recogidas', { method: 'POST', headers: { authorization: 'Bearer ' + token } });
const resumen = await recogida.json();
console.log('recogida:', recogida.status, JSON.stringify(resumen));
if (!resumen.anadidas) {
	console.log('La recogida no ha añadido ninguna convocatoria: no hay eventos que seguir. Prueba con más días (DIAS_ATRAS).');
	process.exit(2);
}

// La traza de esa petición: entra por la pasarela, pasa a la recogida y, por Kafka, llega a la lectura
const tempo = 'http://localhost:3000/api/datasources/proxy/uid/tempo';
let mejor = null;
for (let i = 0; i < 30 && !mejor; i++) {
	await espera(4000);
	const q = encodeURIComponent('{ resource.service.name = "pasarela" && name = "http post /api/v1/recogidas" }');
	const b = await fetch(`${tempo}/api/search?q=${q}&limit=5`).then((x) => (x.ok ? x.json() : { traces: [] })).catch(() => ({ traces: [] }));
	if (!b.traces?.length) continue;
	// Hasta que la lectura haya consumido los eventos y mandado sus spans
	const t = await fetch(`${tempo}/api/traces/${b.traces[0].traceID}`).then((x) => x.json());
	if (JSON.stringify(t).includes('servicio-lectura') || i > 20) mejor = b.traces[0];
}
if (!mejor) {
	console.log('MAL: no aparece la traza de la recogida en Tempo');
	process.exit(1);
}
const traza = await fetch(`${tempo}/api/traces/${mejor.traceID}`).then((x) => x.json());
const servicios = new Map();
for (const lote of traza.batches ?? []) {
	const nombre = lote.resource.attributes.find((a) => a.key === 'service.name')?.value.stringValue;
	const spans = (lote.scopeSpans ?? []).flatMap((s) => s.spans ?? []);
	servicios.set(nombre, (servicios.get(nombre) ?? 0) + spans.length);
}
console.log('traza', mejor.traceID, '→', [...servicios].map(([s, n]) => `${s} (${n} spans)`).join(', '));
const esperados = ['pasarela', 'servicio-recogida', 'servicio-lectura'];
const faltan = esperados.filter((s) => !servicios.has(s));
console.log(faltan.length ? 'MAL: faltan ' + faltan.join(', ') : 'bien: la misma traza cruza pasarela → recogida → Kafka → lectura');
process.exit(faltan.length ? 1 : 0);
