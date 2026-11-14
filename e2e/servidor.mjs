/**
 * Sirve dist/ para las pruebas del navegador, como lo hace Vercel con una web
 * estática: /ruta → ruta, ruta/index.html o ruta.html; si no existe, 404.html
 * con estado 404. (astro preview, sin terminal, se va a segundo plano y
 * Playwright creería que ha terminado.)
 *   node e2e/servidor.mjs 4329
 */
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

const RAIZ = new URL('../dist/', import.meta.url).pathname;
const TIPOS = {
	'.html': 'text/html; charset=utf-8',
	'.js': 'text/javascript; charset=utf-8',
	'.css': 'text/css; charset=utf-8',
	'.json': 'application/json; charset=utf-8',
	'.xml': 'application/xml; charset=utf-8',
	'.ics': 'text/calendar; charset=utf-8',
	'.png': 'image/png',
	'.svg': 'image/svg+xml',
	'.woff2': 'font/woff2',
	'.txt': 'text/plain; charset=utf-8',
};

const fichero = (ruta) => {
	const limpia = normalize(decodeURIComponent(ruta)).replace(/^(\.\.[/\\])+/, '');
	for (const f of [join(RAIZ, limpia), join(RAIZ, limpia, 'index.html'), join(RAIZ, `${limpia}.html`)]) {
		if (f.startsWith(RAIZ) && existsSync(f) && statSync(f).isFile()) return f;
	}
	return null;
};

createServer((req, res) => {
	const ruta = new URL(req.url, 'http://x').pathname;
	const f = fichero(ruta);
	const servido = f ?? join(RAIZ, '404.html');
	res.writeHead(f ? 200 : 404, { 'content-type': TIPOS[extname(servido)] ?? 'application/octet-stream' });
	createReadStream(servido).pipe(res);
}).listen(Number(process.argv[2] ?? 4329));
