/**
 * Pruebas en el navegador (e2e/): los recorridos de la web compilada y su
 * accesibilidad con axe, en móvil y escritorio. Necesitan `npm run build`
 * antes; el servidor lo arranca Playwright (e2e/servidor.mjs, como Vercel).
 *   npx playwright test
 * No dependen de qué ayudas haya cada día: cogen las que encuentran.
 */
import { defineConfig, devices } from '@playwright/test';

const PUERTO = 4329;

export default defineConfig({
	testDir: 'e2e',
	testMatch: '*.e2e.ts',
	timeout: 30_000,
	retries: process.env.CI ? 1 : 0,
	reporter: process.env.CI ? [['github'], ['list']] : 'list',
	use: { baseURL: `http://localhost:${PUERTO}`, trace: 'off' },
	projects: [
		{ name: 'movil', use: { ...devices['Pixel 7'] } },
		{ name: 'escritorio', use: { ...devices['Desktop Chrome'] } },
	],
	webServer: {
		command: `node e2e/servidor.mjs ${PUERTO}`,
		port: PUERTO,
		reuseExistingServer: !process.env.CI,
	},
});
