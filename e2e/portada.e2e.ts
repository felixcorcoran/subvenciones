import { accesible, expect, sinErrores, test } from './ayuda';

test.describe('portada y asistente', () => {
	for (const tema of ['claro', 'oscuro']) {
		test(`la portada es accesible (tema ${tema})`, async ({ page }) => {
			const errores = sinErrores(page);
			await page.goto(`/?tema=${tema}`);
			await expect(page.getByRole('heading', { level: 1 })).toContainText('¿A qué ayudas puede optar tu negocio?');
			await accesible(page);
			expect(errores).toEqual([]);
		});
	}

	test('el asistente, pregunta a pregunta, lleva a los resultados', async ({ page }) => {
		const errores = sinErrores(page);
		await page.goto('/');
		await page.click('#empezar');
		await page.click('button[data-comunidad="ES61"]');
		await page.click('button[data-provincia="ES617"]');
		await page.click('[data-saltar-municipio]');
		await page.click('button[data-forma="autonomo"]');
		await page.click('button[data-empleados="0"]');
		await page.click('button[data-antiguedad="1a3"]');
		await page.click('button[data-sector="I"]');
		await expect(page.locator('[data-paso="finalidad"] h2')).toHaveText('¿Para qué buscas la ayuda?');
		await accesible(page);
		await page.click('button[data-finalidad="digitalizar"]');

		await expect(page.locator('#resumen')).toContainText('puedes pedirlas');
		await expect(page.locator('#respuestas')).toContainText('Málaga');
		await expect(page.locator('#respuestas')).toContainText('Para: digitalizar el negocio');
		await accesible(page);
		// Las respuestas se recuerdan para la próxima vez
		await page.goto('/');
		await expect(page.locator('#anterior')).toContainText('Usa tus respuestas de antes');
		expect(errores).toEqual([]);
	});

	test('el enlace a unos resultados abre directamente sus ayudas', async ({ page }) => {
		await page.goto('/?provincia=ES617&forma=sociedad&sector=I&empleados=1&antiguedad=mas3&para=contratar');
		await expect(page.locator('#resumen')).toContainText('no encajan');
		await expect(page.locator('#respuestas')).toContainText('Empresa (sociedad)');
		await expect(page).toHaveURL(/\/$/);
	});
});

test.describe('todas las ayudas', () => {
	test('buscar, filtrar y guardar', async ({ page, fuera }) => {
		const errores = sinErrores(page);
		await page.goto('/');
		await page.click('#todas');
		const resumen = page.locator('#resumen');
		await expect(resumen).toContainText('convocatorias');
		const total = Number((await resumen.textContent())!.match(/\d+/)![0]);

		await page.fill('#buscar', 'zzzz-nada-coincide');
		await expect(page.locator('.vacio')).toBeVisible();
		await page.fill('#buscar', '');
		await expect(resumen).toContainText(`${total} convocatorias`);

		await page.selectOption('#filtro-comunidad', 'ES61');
		await expect(resumen).toContainText(`de ${total}`);
		await page.selectOption('#filtro-comunidad', '');

		// Guardar una y verla sola
		await page.locator('#grupos .guardar').first().click();
		await expect(page.locator('#grupos .guardar').first()).toHaveAttribute('aria-pressed', 'true');
		await expect(page.locator('#guardadas-filtro')).toContainText('Solo mis guardadas (1)');
		await page.check('#solo-guardadas');
		await expect(page.locator('#grupos li.ayuda')).toHaveCount(1);
		// Al guardar, solo el código de la ayuda va a la cuenta de alertas
		expect(fuera.filter((f) => f.includes('/api/guardadas'))).toHaveLength(1);
		await accesible(page);
		expect(errores).toEqual([]);
	});
});
