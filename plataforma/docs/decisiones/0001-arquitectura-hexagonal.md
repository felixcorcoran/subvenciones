# 0001 Arquitectura hexagonal en cada servicio

- Estado: aceptada
- Fecha: 2026-11-09

## Contexto

Las reglas del encaje son el núcleo del producto y ya viven en tres sitios: la web (TypeScript),
los avisos (Java, repo alertas) y ahora la plataforma. Tienen que responder lo mismo en todos, y
les irán llegando datos por caminos distintos: una llamada REST hoy, eventos de Kafka en la fase 2
y quizá una herramienta MCP más adelante. El almacenamiento también cambia: hoy es un JSON en el
repositorio y aquí es PostgreSQL.

## Decisión

Cada servicio se divide en puertos y adaptadores:

- `dominio`: el modelo y las reglas, en Java sin dependencias. Ni Spring, ni Jackson, ni JDBC.
- `aplicacion`: los casos de uso y los puertos que necesitan (`Convocatorias`). También sin dependencias.
- `adaptadores`: REST, PostgreSQL, catálogos y una capa anticorrupción (`adaptadores/radar`) que
  pasa el JSON de la web al modelo del dominio (fechas de verdad, enumerados en vez de «si» o
  «no_consta», el perfil validado).
- El cableado de Spring vive en la clase de arranque, con métodos `@Bean` explícitos.

El código se escribe en español: convocatoria, bases, autónomo o CNAE son conceptos del dominio
y traducirlos los volvería ambiguos.

Una prueba de ArchUnit vigila estas reglas para que no se vayan rompiendo sin que nadie lo note.

## Consecuencias

- La prueba de contrato ejecuta las reglas de verdad con 3.600 casos en menos de un segundo, sin
  contexto de Spring ni base de datos.
- Añadir Kafka en la fase 2 es añadir un adaptador de entrada; el dominio no cambia.
- Hay más tipos y más código de traducción que en un CRUD por capas. Compensa porque aquí lo
  valioso es la lógica del dominio; un servicio sin reglas de verdad no lo necesitaría.
