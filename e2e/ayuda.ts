/** Lo común a las pruebas del navegador */
import AxeBuilder from '@axe-core/playwright';
import { test as base, expect, type Page } from '@playwright/test';

/**
 * Las pruebas no salen de la web: lo que vaya fuera (la cuenta de guardadas
 * de alertas, las analíticas) se contesta con un 204 sin llegar a ningún sitio.
 * Las peticiones de fuera quedan apuntadas en `fuera`.
 */
export const test = base.extend<{ fuera: string[] }>({
	fuera: async ({ page }, usar) => {
		const fuera: string[] = [];
		await page.route(/^https?:\/\/(?!localhost)/, (ruta) => {
			fuera.push(`${ruta.request().method()} ${ruta.request().url()}`);
			return ruta.fulfill({ status: 204 });
		});
		await usar(fuera);
	},
});
export { expect };

/** Sin fallos de accesibilidad (WCAG 2 A y AA y buenas prácticas) */
export async function accesible(page: Page) {
	const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'best-practice']).analyze();
	expect(r.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

/** Sin errores de JavaScript (los 404 de las analíticas de Vercel, que en local no existen, no cuentan) */
export function sinErrores(page: Page) {
	const errores: string[] = [];
	page.on('pageerror', (e) => errores.push(e.message));
	page.on('console', (m) => {
		if (m.type() === 'error' && !m.text().includes('404')) errores.push(m.text());
	});
	return errores;
}

/** Una ayuda de la lista de hoy (la primera con fecha de cierre, si hay) */
export async function unaAyuda(page: Page): Promise<string> {
	const r = await page.request.get('/convocatorias.json');
	const { convocatorias } = (await r.json()) as { convocatorias: { codigo: string; fin: string | null }[] };
	expect(convocatorias.length).toBeGreaterThan(0);
	return (convocatorias.find((c) => c.fin) ?? convocatorias[0]).codigo;
}

/** El negocio de prueba: autónomo en Málaga, solo, de 1 a 3 años, hostelería */
export const PERFIL = { provincia: 'ES617', municipio: '', forma: 'autonomo', empleados: 0, antiguedad: '1a3', sector: 'I' };
