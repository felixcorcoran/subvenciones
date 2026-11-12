# subvenciones (Radar de subvenciones)

Web (subvenciones.felixcorcoran.es) que recoge cada mañana las convocatorias nuevas de la BDNS, las
resume con Gemini y, con un asistente, dice a cuáles puede optar un negocio. Astro 7 estático,
Node 24. Más detalle en `README.md`.

## Flujo

1. `scripts/recoger.ts` (+ `bdns.ts`, `almacen.ts`, `fichas.ts`): baja convocatorias de la API BDNS
   a `datos/convocatorias.json` y vuelve a comprobar a diario la ficha de las guardadas
   (`comprobada`, `cambios`). Nunca cierra ni borra datos por un fallo de consulta. Rellena hacia
   atrás por tramos (`tramoRelleno`, `datos.relleno`) y guarda lo mínimo de las retiradas en
   `datos/cerradas.json` (su ficha sigue unos meses con `noindex`).
2. `scripts/analizar.ts` (+ `gemini.ts`, `lectura.ts`): Gemini lee el PDF de las bases y rellena
   resumen y condiciones. Cadena de modelos del nivel gratuito; relee si `iaRevisar`.
3. `src/lib/encaje.ts`: en el navegador, clasifica cada ayuda en «Puedes pedirla» / «Revisa» /
   «No encaja», con motivos.
4. `.github/workflows/recoger.yml` lo ejecuta a diario y hace commit de `datos/`; Vercel reconstruye.

## Mapa

- `src/lib/`: `lista.ts` (solo presentación: titular, cercanía, búsqueda, comunidades y sectores,
  `indexable`; no toca el contrato del encaje), `web.ts` (lo que llega al navegador), `visitas.ts` (guardadas y
  visita anterior, en localStorage), `rss.ts`, `imagen.ts` (imagen al compartir), `encaje.ts`, `estado.ts` (abierta / próxima / sin fecha / por verificar / cerrada,
  fuera del contrato con alertas), `fechas.ts` (`cierre`, `fechaLarga`, `hoyMadrid`: usar siempre esta para «hoy»; sin dependencias para que los
  scripts de Node puedan importarlo: `encaje.ts` importa JSON), `sectores.ts` (CNAE-2025), `zonas.ts`,
  `calendario.ts`, `tipos.ts`.
- `scripts/lectura.ts`: `revisarCierreIa` deja en `ia.plazoFin` solo fechas de las bases fiables;
  `limpiarMunicipio` quita de `condiciones.municipio` comunidades o provincias enteras;
  alertas lee `datos/convocatorias.json` directamente, así que la regla vive en los datos.
- `scripts/salud.ts` + `.github/workflows/vigilar.yml`: vigilancia diaria con un issue.
- `src/components/Asistente.astro`: el formulario pregunta a pregunta.
- `src/pages/ayudas/`: ficha de cada convocatoria (y de las cerradas, `Cerrada.astro`);
  `convocatorias.json.ts` y `detalles.json.ts` la exponen como JSON. `comunidades/` (página, `.xml` RSS y `.ics` de plazos), `sectores/`
  (`Listado.astro`, `Tarjeta.astro`), `rss.xml.ts`, `og/`, `404.astro`. Estilos de tarjeta en
  `src/styles/tarjetas.css`. Cabeceras en `vercel.json` (CSP: si se añade un dominio externo, hay que
  ponerlo ahí).
- `src/data/formas.json`, `public/municipios/*.json`: siluetas y municipios (generados con
  `npm run formas` / `npm run catalogos`, no editar a mano).
- `tests/`: Vitest.
- `plataforma/`: servicios Spring Boot (Java 25, Maven multimódulo, hexagonal) para enseñar la
  arquitectura completa; plan en `../felixcorcoran-landing/estrategia/plan-proyecto-empleo.md`. No
  sustituye a la web. Código y documentación en español. Su propio workflow
  (`.github/workflows/plataforma.yml`). Comandos desde `plataforma/`: `./mvnw verify` con
  `JAVA_HOME` apuntando a `~/.jdks/jdk-25.0.4.1+1` (el `java` del PATH es el 8). Las pruebas con
  Testcontainers necesitan Docker: se saltan en local sin él y en CI fallan si se saltan.

## Reglas

- **Nada inventado:** lo que no dice la convocatoria queda como «no consta».
- Si un dato no se puede saber, la ayuda va a «Revisa», nunca a «Puedes pedirla».
- **`encaje.ts` tiene un contrato con el repo `../alertas`**: su `EncajeService` (Java) lo traduce
  frase por frase y se prueba con casos generados desde aquí. Si cambias el encaje, avisa de que
  hay que regenerar los casos en alertas (`npx tsx herramientas/casos-encaje.mts ../subvenciones`)
  y adaptar el Java. Lo mismo para `plataforma/servicio-encaje` (`ReglasDeEncaje`): copiar los casos
  regenerados de `alertas/api/src/test/resources/encaje/casos.json` a
  `plataforma/servicio-encaje/src/test/resources/contrato/casos-encaje.json`.
- Coste cero: no meter servicios de pago; la clave de Gemini debe ser de un proyecto sin facturación.
- `datos/*.json` son grandes y los genera CI: no leerlos enteros, usar búsquedas puntuales.

## Comandos

- `npx astro dev --background`, `npm test`, `npm run check`, `npm run build`.
- `npm run recoger`, `GEMINI_API_KEY=… npm run analizar`. No leer ni mostrar `.env.local`.

No leer: `node_modules/`, `dist/`, `.astro/`, `public/municipios/`, `plataforma/**/target/`.
