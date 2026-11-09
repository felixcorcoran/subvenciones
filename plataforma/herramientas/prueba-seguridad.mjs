// Prueba a mano de la seguridad contra docker compose: tokens de Keycloak y peticiones por la pasarela.
//   node herramientas/prueba-seguridad.mjs infra/keycloak/realm-subvenciones.json
// No imprime tokens ni secretos.
import { readFileSync } from 'node:fs';

const realm = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const secreto = (id) => realm.clients.find((c) => c.clientId === id).secret;
const espera = (ms) => new Promise((r) => setTimeout(r, ms));

async function listo(url, intentos = 90) {
	for (let i = 0; i < intentos; i++) {
		try {
			const r = await fetch(url);
			if (r.ok) return;
		} catch {}
		await espera(2000);
	}
	throw new Error('No responde: ' + url);
}

async function token(cliente) {
	const r = await fetch('http://localhost:8180/realms/subvenciones/protocol/openid-connect/token', {
		method: 'POST',
		headers: { 'content-type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams({ grant_type: 'client_credentials', client_id: cliente, client_secret: secreto(cliente) }),
	});
	if (!r.ok) throw new Error(`token de ${cliente}: ${r.status}`);
	return (await r.json()).access_token;
}

async function pasarela(metodo, ruta, token, cuerpo) {
	const r = await fetch('http://localhost:8000' + ruta, {
		method: metodo,
		headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}) },
		body: cuerpo ? JSON.stringify(cuerpo) : undefined,
	});
	return r.status;
}

await listo('http://localhost:8180/realms/subvenciones');
for (const p of [8000, 8080, 8081, 8082, 8083, 8084]) await listo(`http://localhost:${p}/actuator/health`);

const admin = await token('operaciones');
const sinRol = await token('solo-lectura');
const iss = JSON.parse(Buffer.from(admin.split('.')[1], 'base64url').toString()).iss;
console.log('emisor de los tokens:', iss);

const perfil = { provincia: 'ES617', municipio: '', forma: 'autonomo', empleados: 0, antiguedad: '1a3', sector: 'I' };
const casos = [
	['POST /api/v1/lecturas sin token', await pasarela('POST', '/api/v1/lecturas'), 401],
	['POST /api/v1/lecturas con token sin rol admin', await pasarela('POST', '/api/v1/lecturas', sinRol), 403],
	['POST /api/v1/lecturas con rol admin', await pasarela('POST', '/api/v1/lecturas', admin), 200],
	['POST /api/v1/indices con rol admin', await pasarela('POST', '/api/v1/indices', admin), 200],
	['POST /api/v1/avisos/pasada sin token', await pasarela('POST', '/api/v1/avisos/pasada'), 401],
	['POST /api/v1/encajes sin token (público)', await pasarela('POST', '/api/v1/encajes', null, perfil), 200],
	['GET /api/v1/suscripciones/{token desconocido} (público)', await pasarela('GET', '/api/v1/suscripciones/' + 'a'.repeat(43)), 404],
	['POST /api/v1/recogidas con un token falso', await pasarela('POST', '/api/v1/recogidas', admin.slice(0, -5) + 'xxxxx'), 401],
];
let fallos = 0;
for (const [que, obtenido, esperado] of casos) {
	const bien = obtenido === esperado;
	if (!bien) fallos++;
	console.log(`${bien ? 'bien' : 'MAL '}  ${que}: ${obtenido} (esperado ${esperado})`);
}
process.exit(fallos ? 1 : 0);
