// @ts-check
import { defineConfig, fontProviders } from 'astro/config';

/** @param {number} weight */
const sora = (weight) => `@fontsource/sora/files/sora-latin-${weight}-normal.woff2`;

// Web estática: los datos los recoge cada mañana una GitHub Action
// (scripts/recoger.ts y scripts/analizar.ts) y se publican al reconstruir.
export default defineConfig({
  site: 'https://subvenciones.felixcorcoran.es',
  output: 'static',
  fonts: [
    {
      // La misma Sora que felixcorcoran.es, self-hosted y solo latin
      provider: fontProviders.local(),
      name: 'Sora',
      cssVariable: '--font-sora',
      fallbacks: ['sans-serif'],
      options: {
        variants: [
          { weight: 300, style: 'normal', src: [sora(300)] },
          { weight: 400, style: 'normal', src: [sora(400)] },
          { weight: 500, style: 'normal', src: [sora(500)] },
          { weight: 600, style: 'normal', src: [sora(600)] },
        ],
      },
    },
  ],
  build: {
    inlineStylesheets: 'always',
  },
});
