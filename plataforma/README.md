# Plataforma del Radar de subvenciones

> *In English:* the service architecture behind the live grants radar. Spring Boot services with
> hexagonal architecture, Kafka, PostgreSQL, Testcontainers, Docker, Kubernetes, OpenTelemetry and
> OAuth2. The matching rules are tested against the live website's own code.

El radar que está en marcha ([subvenciones.felixcorcoran.es](https://subvenciones.felixcorcoran.es))
funciona a coste cero con una web estática y tareas programadas: este repositorio y
[alertas](https://github.com/felixcorcoran/alertas). Esta carpeta lo reconstruye como lo
llevaría una empresa: servicios Spring Boot con arquitectura hexagonal, Kafka entre servicios,
PostgreSQL, contenedores, Kubernetes, observabilidad y OAuth2. Las reglas son las de verdad y se
prueban contra la web en producción.

La web sigue funcionando igual: esta carpeta tiene su propio workflow
([`plataforma.yml`](../.github/workflows/plataforma.yml)), que solo se lanza cuando cambia.

## Estado

| Fase | Qué | Estado |
| --- | --- | --- |
| 1. Base | `servicio-encaje` hexagonal; PostgreSQL + Flyway; Testcontainers; CI; Docker | hecha |
| 2. Servicios y eventos | recogida, lectura con IA, encaje y avisos unidos por Kafka; idempotencia, DLT y outbox | hecha |
| 3. IA medida | «Pregunta a la convocatoria»: RAG con pgvector, citas literales comprobadas y evaluación con datos en CI | hecha |
| 4. Seguridad | OAuth2 / OIDC con Keycloak; cada servicio, resource server con Spring Security; pasarela con CORS y límite por IP | hecha |
| 5. Operación | Kubernetes (kind + Helm), OpenTelemetry + Grafana, AWS con Terraform | pendiente |

## Cómo encaja todo

```
                    «convocatorias»                       «convocatorias.leidas»
BDNS ─► recogida ──────────────────────► lectura (Gemini) ─────────────┬──────────► encaje ◄── POST /api/v1/encajes
       (6:30)     publicada / cambiada /    (cada hora)                 │            (REST)            │
                  cerrada                                               ├──────────► avisos ───────────┘
                                                                        │            (9:00) ──► correo
                                                                        └──────────► preguntas (pgvector + Gemini)
                                                                                     ◄── POST /api/v1/convocatorias/{codigo}/preguntas
```

Cada servicio tiene su base de datos PostgreSQL y se habla con los demás solo por Kafka (y los
avisos, por REST con el encaje: [decisión 0005](docs/decisiones/0005-avisos-preguntan-al-encaje.md)).
Quien publica lo hace con outbox; quien consume, con idempotencia y cola de errores.

Lo siguiente: Kubernetes, OpenTelemetry y AWS.

## Seguridad y pasarela

Desde fuera solo se entra por la **pasarela** (Spring Cloud Gateway, puerto 8000), que reparte cada
ruta a su servicio, atiende CORS para las webs del radar y limita por IP las rutas públicas
(preguntar, apuntarse, el encaje). La identidad la da **Keycloak** (OAuth2 / OIDC): cada servicio
es un *resource server* que valida el JWT y decide con sus propias reglas
([decisión 0007](docs/decisiones/0007-seguridad-oauth2.md)):

| Rutas | Acceso |
| --- | --- |
| `POST /api/v1/encajes`, `POST /api/v1/convocatorias/{codigo}/preguntas`, `/api/v1/suscripciones/**`, `POST /api/v1/baja/**` | públicas (las suscripciones, con el token del enlace del correo) |
| `POST /api/v1/recogidas`, `/lecturas`, `/indices`, `/avisos/pasada`, `PUT /api/v1/convocatorias/{codigo}` | rol `admin` de Keycloak |
| `GET /actuator/health` | pública |
| todo lo demás | usuario autenticado |

Las reglas son comunes (módulo `seguridad`, una autoconfiguración) y cada servicio solo declara sus
listas. Se prueban con un Keycloak de verdad en Testcontainers, en cada servicio (401 sin token, 403
sin rol) y de punta a punta en `docker compose` con `herramientas/prueba-seguridad.mjs`.

## servicio-recogida

Cada mañana (6:30, hora de Madrid) busca en la API pública de la BDNS las convocatorias para
empresas registradas desde la última pasada, guarda las que se pueden pedir y vuelve a leer la
ficha de las guardadas. Lo traduce de `scripts/recoger.ts` y `scripts/fichas.ts` con las mismas
reglas:

- Se descartan las de concesión directa y las ya cerradas.
- Un fallo de consulta nunca cierra ni borra nada: sin ficha completa, la convocatoria se queda
  como estaba y se reintenta.
- Si la ficha deja de traer un dato que antes traía, se conserva el anterior.
- Solo se retiran las que han cerrado según la fecha de la BDNS.

Cada cambio sale como evento en el tópico `convocatorias` (clave = código):
`convocatoria.publicada`, `convocatoria.cambiada` (con los campos que han cambiado) y
`convocatoria.cerrada`. Se publican con una **outbox transaccional**: el evento se guarda en la misma
transacción que la convocatoria y un relevo lo envía a Kafka, así que no se pierde aunque el
servicio caiga a mitad de pasada ([decisión 0004](docs/decisiones/0004-outbox.md)).

```
POST /api/v1/recogidas   lanza una pasada a mano y devuelve su resumen (en la fase 4, solo administradores)
```

Contra la BDNS real, la primera pasada (14 días) encontró 300 convocatorias para empresas, guardó
118 y descartó 182 (178 de concesión directa y 4 cerradas), en unos dos minutos con la pausa entre
peticiones.

## servicio-lectura

Escucha el tópico `convocatorias` y lee con Gemini las bases de cada convocatoria nueva, como
`scripts/analizar.ts`, `gemini.ts` y `lectura.ts`: qué financia, para quién, cuánto, el plazo, los
requisitos y las condiciones del formulario. Solo lo que dice el documento: lo que no consta queda
como «no consta».

- **Nivel gratuito:** una cadena de modelos. Si uno agota su cupo del día o ya no existe, no se
  vuelve a probar hasta el día siguiente; si ninguno puede responder, lo pendiente sigue en la
  siguiente pasada (cada hora de 7:00 a 22:00).
- **No se fía de la respuesta:** se valida antes de guardarla; una respuesta a medias cuenta como
  intento fallido. Tras tres fallos espera dos semanas; un documento nuevo da otra oportunidad.
- **Fecha de cierre de las bases:** solo se usa si no contradice a la BDNS, el plazo leído la nombra
  y no hay dudas sobre él (con la excepción del plazo previo al periodo de la BDNS).
- **Relee** si la recogida avisa de que cambió el documento o el plazo; mientras, mantiene lo leído.
- Publica `convocatoria.leida` en `convocatorias.leidas` con outbox: la convocatoria entera tal como
  la publicó la recogida, más `ia`. Es el mismo formato que `datos/convocatorias.json` de la web.

```
POST /api/v1/lecturas   lanza una pasada a mano (en la fase 4, solo administradores)
```

## servicio-avisos

Las suscripciones y los correos, como el repositorio [alertas](https://github.com/felixcorcoran/alertas):

- Alta con doble confirmación (la respuesta es la misma esté o no el email apuntado), cambios y
  baja con un clic (`List-Unsubscribe`, RFC 8058). Solo se guarda el email, el perfil del negocio y
  unas fechas; el token de 256 bits de cada enlace es la única llave a sus datos.
- Escucha `convocatorias.leidas`. Cada mañana (9:00) reparte las ayudas abiertas que han llegado
  desde la última pasada: pregunta al encaje una vez por persona, crea un aviso si puede pedirla o
  tiene que revisarla y manda un correo por persona, con un tope diario.
- La primera pasada no avisa de lo que ya estaba publicado.
- En local, los correos van a Mailpit (http://localhost:8025) y no salen de la máquina.

```
POST   /api/v1/suscripciones              alta (email, perfil del formulario, acepto)
POST   /api/v1/suscripciones/confirmar    confirmar con el token del correo
GET    /api/v1/suscripciones/{token}      ver
PUT    /api/v1/suscripciones/{token}      cambiar el negocio
DELETE /api/v1/suscripciones/{token}      baja
POST   /api/v1/baja/{token}               baja con un clic desde el gestor de correo
POST   /api/v1/avisos/pasada              lanza la pasada a mano (en la fase 4, solo administradores)
```

## servicio-preguntas

«Pregunta a la convocatoria»: preguntas en lenguaje normal sobre las bases de una convocatoria,
respondidas **solo con citas literales de las bases y su página**, o «no consta».

```
POST /api/v1/convocatorias/939979/preguntas   {"pregunta":"¿Pueden pedirla los autónomos?"}
→ {"texto":"Sí, …","consta":true,"citas":[{"pagina":2,"texto":"las personas físicas que se encuentren en alta en el Régimen Especial de Trabajadores Autónomos (RETA)"}], …}
```

- **RAG con pgvector:** el PDF se parte en pasajes que no cruzan de página, con su vector
  (embeddings de Gemini, 768 dimensiones, índice HNSW). Se buscan los 6 más parecidos de esa
  convocatoria y Gemini responde con ellos.
- **Nada inventado, comprobado en código:** cada cita tiene que estar tal cual en su pasaje; si no
  queda ninguna, la respuesta es «no consta». Un PDF escaneado se marca y se dice que esas bases no
  se pueden consultar ([decisión 0006](docs/decisiones/0006-respuestas-con-citas.md)).
- **Evaluación con datos** (`EvaluacionTest`): 20 preguntas reales sobre las bases de cuatro
  convocatorias, con su respuesta y su página, más una convocatoria escaneada. Mide si acierta cuándo
  consta, si inventa (tiene que ser 0), si cita la página buena y si la respuesta lleva el dato. En
  cada push se pasa con las respuestas de Gemini grabadas; el workflow
  [`evaluacion.yml`](../.github/workflows/evaluacion.yml) la pasa a mano contra Gemini de verdad.

Resultado contra Gemini el 9 de noviembre de 2026 (las respuestas esperadas están pendientes de
revisión humana):

| Métrica | Resultado |
| --- | --- |
| Inventa una respuesta cuando las bases no lo dicen | 0 de 4 |
| Citas descartadas por no ser literales | 0 de 17 |
| Acierta si consta o no | 18 de 20 (90 %) |
| Cita en la página buena | 14 de 16 (88 %) |
| Respuesta con el dato esperado | 13 de 16 (81 %) |

Los fallos son del lado prudente: dos «no consta» cuando sí constaba y una respuesta que da el
presupuesto total de la convocatoria en vez del máximo por empresa (con su cita, en la página
buena, pero sin responder a lo que se preguntaba).

## servicio-encaje

Dice si un negocio puede pedir una ayuda: **sí**, **revisa** o **no**, siempre con sus motivos.
Regla de oro: si un dato no se puede saber, la ayuda va a *revisa*, nunca a *sí*.

```
encaje/
  dominio/       Convocatoria, Perfil, Encaje, ReglasDeEncaje, Catalogo (puerto). Java sin dependencias.
  aplicacion/    ServicioDeEncaje (casos de uso), Convocatorias (puerto). Java sin dependencias.
  adaptadores/
    entrada/web/ API REST
    salida/      PostgreSQL (JdbcClient + JSONB) y catálogos (provincias y sectores)
    radar/       capa anticorrupción: el JSON de la web al modelo del dominio
```

`ArquitecturaTest` (ArchUnit) rompe el build si el dominio o la aplicación dependen de Spring,
Jackson, JDBC o de los adaptadores. Ver la [decisión 0001](docs/decisiones/0001-arquitectura-hexagonal.md).

### La misma respuesta que la web

`ContratoConLaWebTest` repite **3.600 casos** (300 convocatorias reales y de prueba × 60 negocios)
calculados por el propio `src/lib/encaje.ts` de la web y exige el mismo estado y los mismos
motivos, en el mismo orden.

### Entradas

- **Kafka**, tópico `convocatorias.leidas`: eventos `convocatoria.leida` con la clave = código. Cada
  evento se aplica una sola vez aunque llegue repetido, y lo que no se puede aplicar va a
  `convocatorias.leidas.dlt` sin bloquear lo demás. Ver la [decisión 0003](docs/decisiones/0003-eventos-kafka.md).
- **REST:**

```
PUT  /api/v1/convocatorias/{codigo}   una convocatoria en el formato de la web (204)
POST /api/v1/encajes                  un perfil → las convocatorias abiertas, primero las que puede pedir
```

```sh
curl -X POST localhost:8080/api/v1/encajes -H 'Content-Type: application/json' \
  -d '{"provincia":"ES617","municipio":"","forma":"autonomo","empleados":0,"antiguedad":"1a3","sector":"I"}'
```

## En local

Hace falta JDK 25 y Docker. Desde `plataforma/`:

```sh
./mvnw verify                 # unitarias, contrato, arquitectura, PostgreSQL y Kafka con Testcontainers
docker compose up --build     # PostgreSQL, Kafka, Mailpit y los cuatro servicios
```

| Servicio | Puerto | Para lanzarlo a mano |
| --- | --- | --- |
| **pasarela** | 8000 | la entrada: todas las rutas de abajo, con CORS y límite por IP |
| Keycloak | 8180 | tokens: `POST /realms/subvenciones/protocol/openid-connect/token` |
| servicio-encaje | 8080 | `POST /api/v1/encajes` con un perfil |
| servicio-recogida | 8081 | `POST /api/v1/recogidas` (contra la BDNS real) |
| servicio-lectura | 8082 | `POST /api/v1/lecturas` (con `GEMINI_API_KEY` en el entorno; 3 por pasada en local) |
| servicio-avisos | 8083 | `POST /api/v1/suscripciones`, `POST /api/v1/avisos/pasada` |
| servicio-preguntas | 8084 | `POST /api/v1/convocatorias/{codigo}/preguntas` (con `GEMINI_API_KEY`) |
| Mailpit | 8025 | los correos, sin que salgan de la máquina |

En local nada está programado: cada pasada se lanza a mano, en orden (avisos para fijar lo ya
visto, recogida, lectura, avisos), con un token del cliente `operaciones`, que tiene el rol `admin`.
Su secreto es solo para local y está en `infra/keycloak/realm-subvenciones.json`:

```sh
TOKEN=$(curl -s localhost:8180/realms/subvenciones/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=operaciones -d client_secret=<secreto del realm local> \
  | jq -r .access_token)
curl -X POST localhost:8000/api/v1/recogidas -H "Authorization: Bearer $TOKEN"
```

Para cargar en el encaje las convocatorias ya leídas de la web como eventos (en Git Bash, con
`MSYS_NO_PATHCONV=1` delante de `docker compose exec`):

```sh
node herramientas/eventos-convocatorias.mjs ../datos/convocatorias.json \
  | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server localhost:29092 --topic convocatorias.leidas \
      --property parse.key=true --property key.separator=$'\t'
```

Sin Docker, las pruebas con Testcontainers se saltan en local; en CI, si alguna se salta, falla.

## Decisiones

- [0001 Arquitectura hexagonal](docs/decisiones/0001-arquitectura-hexagonal.md)
- [0002 La plataforma, en el mismo repositorio que la web](docs/decisiones/0002-mismo-repositorio.md)
- [0003 Los servicios se hablan con eventos de Kafka](docs/decisiones/0003-eventos-kafka.md)
- [0004 Outbox transaccional para publicar eventos](docs/decisiones/0004-outbox.md)
- [0005 Los avisos preguntan al servicio de encaje por REST](docs/decisiones/0005-avisos-preguntan-al-encaje.md)
- [0006 Respuestas sobre las bases: solo con citas literales comprobadas](docs/decisiones/0006-respuestas-con-citas.md)
- [0007 Seguridad: OAuth2 con Keycloak, validada en cada servicio](docs/decisiones/0007-seguridad-oauth2.md)
