# Radar de subvenciones

[![Pruebas](https://github.com/felixcorcoran/subvenciones/actions/workflows/pruebas.yml/badge.svg)](https://github.com/felixcorcoran/subvenciones/actions/workflows/pruebas.yml)

Cada mañana recoge las convocatorias de ayudas nuevas de toda España para autónomos y empresas, lee sus bases con IA y, con un formulario, te dice a cuáles puede optar tu negocio y por qué.

**En marcha:** [subvenciones.felixcorcoran.es](https://subvenciones.felixcorcoran.es)

## Cómo funciona

1. **Recoger** (`scripts/recoger.ts`): consulta la API pública del [Sistema Nacional de Publicidad de Subvenciones](https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias) (BDNS) por las convocatorias registradas para empresas (pymes, autónomos y grandes empresas) y baja la ficha de cada una: zona, sector, plazos, presupuesto y documentos. Descarta las de concesión directa, que van a una entidad concreta, y retira las que han cerrado.
   - **Comprobar las guardadas** (`scripts/fichas.ts`): cada día vuelve a pedir la ficha de las que ya tiene y apunta lo que cambia (plazo, importe, zona, documentos…) y la fecha de la comprobación. Si la ficha no llega o llega incompleta, la convocatoria se queda como estaba: un fallo de consulta nunca la cierra ni borra datos, y un dato que la ficha deja de traer se conserva. Si cambia el plazo o el documento de las bases, la IA la vuelve a leer; si no, no se gasta otra llamada.
   - **Fecha de cierre** (`scripts/lectura.ts`): manda la de la ficha de la BDNS. La que la IA saca de las bases solo se usa si la BDNS no da ninguna (o da la misma) y es clara: el texto del plazo la nombra, no es anterior al registro y la lectura no tiene dudas sobre el plazo. Si no, se aparta en `cierreDescartado` con el motivo y no cuenta. Única excepción, el **plazo previo** (`plazoPrevio` en `src/lib/fechas.ts`): si la fecha de las bases es anterior al inicio de la BDNS, no es anterior al registro y el texto del plazo la nombra (por ejemplo, la adhesión de comercios a una campaña de bonos antes del periodo de los bonos), esa fecha es el cierre para el negocio y la ficha enseña aparte las fechas de la BDNS. Pasada esa fecha, la ayuda sale de la lista sin decirse cerrada; solo se retira cuando pasa también el fin de la BDNS. Así una fecha dudosa nunca cierra una convocatoria, y el repo de alertas, que lee estos datos, usa la misma fecha sin repetir la regla.
   - **Retirar**: solo las que han cerrado con una fecha fiable. Las que no tienen fecha de cierre se quedan en los datos; si llevan más de cuatro meses así, la web las aparta de la lista y del sitemap como «por verificar» (su ficha sigue publicada con el aviso «puede que ya no esté abierta»), sin decir que han cerrado (`src/lib/estado.ts`). Se siguen comprobando en la BDNS una vez por semana.
   - En la web, la portada cuenta por separado las que tienen plazo abierto, las que abren pronto y las que no tienen fecha de cierre confirmada; la fecha que viene de las bases se marca «(según las bases)», y las que llevan más de tres días sin poder comprobarse lo avisan.
2. **Leer las bases** (`scripts/analizar.ts`): Gemini lee el PDF de cada convocatoria y saca en lenguaje claro qué financia, para quién, cuánto, el plazo, los requisitos y en qué se puede gastar. También las condiciones que usa el formulario (si admite autónomos o sociedades, plantilla máxima, antigüedad, municipio). Solo lo que dice el documento: lo que no consta se queda como «no consta». La respuesta se valida antes de guardarse. Si un modelo está retirado, saturado o no responde, se pasa al siguiente sin contarlo contra la convocatoria; tras tres fallos de la propia convocatoria se espera dos semanas y se vuelve a probar (`scripts/lectura.ts`).
3. **Encajar** (`src/lib/encaje.ts`): en el navegador, compara el perfil del formulario con cada convocatoria y la deja en «Puedes pedirla», «Revisa» o «No encaja», siempre con sus motivos. Si un dato no se puede saber, la ayuda va a «Revisa», nunca a «Puedes pedirla».

Una GitHub Action ([`recoger.yml`](.github/workflows/recoger.yml)) lo ejecuta cada mañana y sube los datos (`datos/`); Vercel reconstruye la web.

**Vigilancia** ([`vigilar.yml`](.github/workflows/vigilar.yml), `scripts/salud.ts`): cada día a las 10:00 UTC mira lo que dejan apuntado la recogida y la lectura en `datos.salud`. Abre un issue «Radar: la recogida diaria necesita atención» si no hay una recogida completa en más de 30 horas (fallo de la ejecución), si la BDNS deja sin devolver muchas fichas o la IA lleva tres días sin leer nada con bases pendientes (fallo parcial). Una pasada correcta sin cambios no avisa. Hay un solo issue abierto a la vez: se actualiza sin volver a notificar y se cierra solo cuando todo vuelve a estar en orden. Usa el token de la propia Action, sin secretos nuevos.

## Coste: cero

- La API de la BDNS es abierta y su aviso legal permite reutilizar los datos, también con fines comerciales. Se consulta una vez al día, con pausa entre peticiones.
- Gemini en su nivel gratuito, con una cadena de modelos: si uno agota su cupo diario se pasa al siguiente, y lo pendiente sigue al día siguiente.
- GitHub Actions y Vercel, en sus planes gratuitos.

## En local

```sh
npm install
npm run recoger              # convocatorias de los últimos 14 días
GEMINI_API_KEY=… npm run analizar
npm run dev
npm test
```

Las pruebas (Vitest, en `tests/`) cubren el encaje de cada ayuda con un negocio (zona, municipio, sector, forma jurídica, plazos), la tabla de sectores de la CNAE-2025, los nombres de zonas y el evento de calendario. Se pasan en cada push con GitHub Actions, junto a `astro check` y el build.

La clave de Gemini tiene que ser de un proyecto de Google AI Studio sin facturación, para quedarse en el nivel gratuito.

---

Hecho por [Félix Corcoran](https://dev.felixcorcoran.es). Los resúmenes son orientativos: lo que vale es la convocatoria oficial.
