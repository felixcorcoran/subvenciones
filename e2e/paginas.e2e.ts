import { accesible, expect, PERFIL, sinErrores, test, unaAyuda } from './ayuda';

test.describe('ficha de una ayuda', () => {
	test('se lee, es accesible y se puede guardar', async ({ page, fuera }) => {
		const errores = sinErrores(page);
		const codigo = await unaAyuda(page);
		await page.goto(`/ayudas/${codigo}`);
		await expect(page.getByRole('heading', { level: 1 })).not.toBeEmpty();
		await expect(page.locator('.enlaces a', { hasText: 'Convocatoria oficial (BDNS)' })).toHaveAttribute('href', /infosubvenciones/);
		await expect(page.locator('.compartir')).toContainText('WhatsApp');
		await page.click('[data-guardar]');
		await expect(page.locator('[data-guardar]')).toHaveText('★ Guardada');
		expect(fuera.some((f) => f.startsWith('POST') && f.includes('/api/guardadas'))).toBe(true);
		await accesible(page);
		expect(errores).toEqual([]);
	});

	test('«Qué hacer ahora»: las casillas se recuerdan y al imprimir solo queda lo útil', async ({ page }) => {
		const codigo = await unaAyuda(page);
		await page.goto(`/ayudas/${codigo}`);
		const pasos = page.locator('#que-hacer input[data-paso]');
		expect(await pasos.count()).toBeGreaterThanOrEqual(3);
		await pasos.first().check();
		await expect(page.locator('[data-progreso]')).toHaveText(new RegExp(`1 de ${await pasos.count()} hechos`));
		await page.reload();
		await expect(pasos.first()).toBeChecked();
		await accesible(page);

		await page.emulateMedia({ media: 'print' });
		await expect(page.locator('#que-hacer')).toBeVisible();
		await expect(page.locator('header.nav')).toBeHidden();
		await expect(page.locator('[data-imprimir]')).toBeHidden();
	});

	test('con respuestas guardadas, dice ahí mismo si encaja', async ({ page }) => {
		const codigo = await unaAyuda(page);
		await page.goto('/');
		await page.evaluate((p) => localStorage.setItem('perfil-subvenciones', JSON.stringify(p)), PERFIL);
		await page.goto(`/ayudas/${codigo}`);
		await expect(page.locator('#veredicto')).toBeVisible();
		await expect(page.locator('#veredicto [data-titulo]')).toHaveText(/Puedes pedirla|Revisa los detalles|No encaja/);
		await expect(page.locator('[data-pedirla]')).toHaveCount(0);
	});

	test('una ayuda que ya no está lleva a la BDNS', async ({ page }) => {
		const r = await page.goto('/ayudas/111');
		expect(r?.status()).toBe(404);
		await expect(page.getByRole('heading', { level: 1 })).toHaveText('Esta ayuda ya no está en el radar');
		await expect(page.locator('[data-oficial]')).toHaveAttribute('href', /convocatorias\/111$/);
		await accesible(page);
	});
});

test.describe('páginas por zona y sector', () => {
	for (const ruta of ['/comunidades/andalucia', '/provincias/malaga', '/sectores/hosteleria']) {
		test(`${ruta} se lee y es accesible`, async ({ page }) => {
			const errores = sinErrores(page);
			await page.goto(ruta);
			await expect(page.getByRole('heading', { level: 1 })).toContainText('Ayudas para');
			await expect(page.locator('.lead')).toContainText('convocatoria');
			await accesible(page);
			expect(errores).toEqual([]);
		});
	}

	test('los calendarios y RSS de una comunidad responden', async ({ page }) => {
		const ics = await page.request.get('/comunidades/andalucia.ics');
		expect(await ics.text()).toContain('BEGIN:VCALENDAR');
		const rss = await page.request.get('/comunidades/andalucia.xml');
		expect(await rss.text()).toContain('<rss');
	});
});
