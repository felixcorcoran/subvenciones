# subvenciones (Radar de subvenciones)

Web (subvenciones.felixcorcoran.es) que recoge cada mañana las convocatorias nuevas de la BDNS, las
resume con Gemini y, con un asistente, dice a cuáles puede optar un negocio. Astro 7 estático,
Node 24. Más detalle en `README.md`.

## Flujo

1. `scripts/recoger.ts` (+ `bdns.ts`, `almacen.ts`, `fichas.ts`): baja convocatorias de la API BDNS
   a `datos/convocatorias.json` y vuelve a comprobar a diario la ficha de las guardadas
   (`comprobada`, `cambios`). Nunca cierra ni borra datos por un fallo de consulta.
2. `scripts/analizar.ts` (+ `gemini.ts`, `lectura.ts`): Gemini lee el PDF de las bases y rellena
   resumen y condiciones. Cadena de modelos del nivel gratuito; relee si `iaRevisar`.
3. `src/lib/encaje.ts`: en el navegador, clasifica cada ayuda en «Puedes pedirla» / «Revisa» /
   «No encaja», con motivos.
4. `.github/workflows/recoger.yml` lo ejecuta a diario y hace commit de `datos/`; Vercel reconstruye.

## Mapa

- `src/lib/`: `encaje.ts`, `estado.ts` (abierta / próxima / sin fecha / por verificar / cerrada,
  fuera del contrato con alertas), `fechas.ts` (`cierre`, `fechaLarga`; sin dependencias para que los
  scripts de Node puedan importarlo: `encaje.ts` importa JSON), `sectores.ts` (CNAE-2025), `zonas.ts`,
  `calendario.ts`, `tipos.ts`.
- `scripts/lectura.ts`: `revisarCierreIa` deja en `ia.plazoFin` solo fechas de las bases fiables;
  alertas lee `datos/convocatorias.json` directamente, así que la regla vive en los datos.
- `scripts/salud.ts` + `.github/workflows/vigilar.yml`: vigilancia diaria con un issue.
- `src/components/Asistente.astro`: el formulario pregunta a pregunta.
- `src/pages/ayudas/`: ficha de cada convocatoria; `convocatorias.json.ts` la expone como JSON.
- `src/data/formas.json`, `public/municipios/*.json`: siluetas y municipios (generados con
  `npm run formas` / `npm run catalogos`, no editar a mano).
- `tests/`: Vitest.

## Reglas

- **Nada inventado:** lo que no dice la convocatoria queda como «no consta».
- Si un dato no se puede saber, la ayuda va a «Revisa», nunca a «Puedes pedirla».
- **`encaje.ts` tiene un contrato con el repo `../alertas`**: su `EncajeService` (Java) lo traduce
  frase por frase y se prueba con casos generados desde aquí. Si cambias el encaje, avisa de que
  hay que regenerar los casos en alertas (`npx tsx herramientas/casos-encaje.mts ../subvenciones`)
  y adaptar el Java.
- Coste cero: no meter servicios de pago; la clave de Gemini debe ser de un proyecto sin facturación.
- `datos/*.json` son grandes y los genera CI: no leerlos enteros, usar búsquedas puntuales.

## Comandos

- `npx astro dev --background`, `npm test`, `npm run check`, `npm run build`.
- `npm run recoger`, `GEMINI_API_KEY=… npm run analizar`. No leer ni mostrar `.env.local`.

No leer: `node_modules/`, `dist/`, `.astro/`, `public/municipios/`.
