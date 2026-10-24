# Radar de subvenciones

Cada mañana recoge las convocatorias de ayudas nuevas de toda España para autónomos y empresas, lee sus bases con IA y, con un formulario, te dice a cuáles puede optar tu negocio y por qué.

**En marcha:** [subvenciones.felixcorcoran.es](https://subvenciones.felixcorcoran.es)

## Cómo funciona

1. **Recoger** (`scripts/recoger.ts`): consulta la API pública del [Sistema Nacional de Publicidad de Subvenciones](https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias) (BDNS) por las convocatorias registradas para empresas (pymes, autónomos y grandes empresas) y baja la ficha de cada una: zona, sector, plazos, presupuesto y documentos. Descarta las de concesión directa, que van a una entidad concreta, y retira las que han cerrado.
2. **Leer las bases** (`scripts/analizar.ts`): Gemini lee el PDF de cada convocatoria y saca en lenguaje claro qué financia, para quién, cuánto, el plazo, los requisitos y en qué se puede gastar. También las condiciones que usa el formulario (si admite autónomos o sociedades, plantilla máxima, antigüedad, municipio). Solo lo que dice el documento: lo que no consta se queda como «no consta».
3. **Encajar** (`src/lib/encaje.ts`): en el navegador, compara el perfil del formulario con cada convocatoria y la deja en «Puedes pedirla», «Revisa» o «No encaja», siempre con sus motivos. Si un dato no se puede saber, la ayuda va a «Revisa», nunca a «Puedes pedirla».

Una GitHub Action ([`recoger.yml`](.github/workflows/recoger.yml)) lo ejecuta cada mañana y sube los datos (`datos/`); Vercel reconstruye la web.

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
```

La clave de Gemini tiene que ser de un proyecto de Google AI Studio sin facturación, para quedarse en el nivel gratuito.

---

Hecho por [Félix Corcoran](https://dev.felixcorcoran.es). Los resúmenes son orientativos: lo que vale es la convocatoria oficial.
