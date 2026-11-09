# 0002 La plataforma, en el mismo repositorio que la web

- Estado: aceptada
- Fecha: 2026-11-09

## Contexto

La web en producción (Astro, Vercel, recogida diaria con GitHub Actions) y la plataforma de
servicios son el mismo producto. Separarlas en dos repositorios lo partiría en dos y obligaría a
explicar cuál es «el de verdad». Pero la web no puede romperse por lo que se haga en la plataforma.

## Decisión

La plataforma vive en `plataforma/`, como un proyecto Maven multimódulo: un `pom.xml` padre con
las versiones comunes y un módulo por servicio. La web y la plataforma no comparten código; la
plataforma lee los datos en el mismo formato que publica la web, y la prueba de contrato usa casos
calculados por la propia web.

- `.github/workflows/plataforma.yml` solo se lanza cuando cambia `plataforma/`.
- Vercel construye la web de la raíz y no ve el Java.
- Cada servicio tiene su imagen y su propio historial de migraciones de Flyway, y los servicios
  solo se hablan por REST y Kafka.

## Consecuencias

- Un cambio en las reglas de la web y su traducción en Java pueden ir en el mismo pull request.
- La recogida diaria hace commits en `datos/`, pero no lanza las pruebas de la plataforma.
- El repositorio mezcla Node y Java. Cada parte tiene su carpeta, sus comandos y su workflow, y el
  README de cada una dice cómo se usa.
