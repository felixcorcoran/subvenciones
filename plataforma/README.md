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
| 5. Operación | hecho: OpenTelemetry (trazas que cruzan Kafka y la outbox, métricas) con Grafana, panel propio y alertas por correo; Kubernetes en local (kind + Helm); AWS con Terraform (EKS, RDS, ECR) escrito y validado. Falta: aplicarlo y grabar la demo | **en marcha** |

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

Lo siguiente: levantar la demo en AWS, grabarla y apagarla.

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

## Observabilidad

Todos los servicios mandan trazas y métricas por **OpenTelemetry** (OTLP). En local las recibe
`grafana/otel-lgtm` y se ven en Grafana (http://localhost:3000). Una recogida lanzada por la
pasarela se sigue en **una sola traza**: pasarela → recogida (con cada llamada a la BDNS) →
publicación de cada evento por la outbox → consumo en la lectura. La traza no se corta en la outbox
porque el contexto se guarda con el evento y el relevo la continúa al publicar
([decisión 0008](docs/decisiones/0008-observabilidad.md)).

```sh
node herramientas/prueba-trazas.mjs infra/keycloak/realm-subvenciones.json   # con docker compose arriba
```

**Panel y alertas** ([`infra/grafana`](infra/grafana), cargados al arrancar Grafana): el panel
*Subvenciones › Plataforma* enseña peticiones, latencia p95 y errores por servicio, el retraso de
los consumidores de Kafka, la outbox, las colas de errores y la memoria y CPU de cada JVM. Dos
métricas son propias (módulo `observabilidad`):

- `subvenciones.kafka.cola.errores`: mensajes que acaban en una cola de errores, por tópico. Se
  engancha solo a cualquier `DefaultErrorHandler` del servicio.
- `subvenciones.outbox.pendientes` y `subvenciones.outbox.antiguedad`: eventos sin publicar y
  segundos que lleva esperando el más antiguo.

| Alerta | Salta cuando |
| --- | --- |
| Mensajes en una cola de errores | algún mensaje va a una DLT |
| La outbox no publica | un evento lleva más de 5 minutos sin salir a Kafka |
| Errores 5xx | un servicio da más de 5 en 10 minutos |
| Un servicio ha dejado de mandar métricas | mandaba en la última hora y lleva 3 minutos callado |

Avisan por correo; en local llegan a Mailpit (http://localhost:8025). Probado así: un mensaje roto
en `convocatorias.leidas` fue a las colas de errores de encaje, avisos y preguntas, y llegaron los
tres correos; al parar `servicio-encaje`, llegó el de «ha dejado de mandar métricas».

## Kubernetes

El chart de Helm ([`despliegue/helm/subvenciones`](despliegue/helm/subvenciones)) despliega los cinco
servicios y la pasarela con una sola plantilla: comprobaciones de arranque, vida y disponibilidad
(las que publica Spring Boot en Kubernetes), límites de memoria, usuario sin privilegios y los
secretos en un `Secret`. Para un clúster local incluye también PostgreSQL, Kafka, Keycloak, Grafana
y Mailpit (`infra.activada`); en uno de verdad serían servicios gestionados.

En local con [kind](https://kind.sigs.k8s.io), tras construir las imágenes con `docker compose build`:

```sh
kind create cluster --name subvenciones --image kindest/node:v1.33.1
for s in servicio-encaje servicio-recogida servicio-lectura servicio-avisos servicio-preguntas pasarela; do
  docker tag plataforma-$s subvenciones/$s:local && kind load docker-image --name subvenciones subvenciones/$s:local
done
helm install subvenciones despliegue/helm/subvenciones --wait --timeout 10m
kubectl port-forward svc/pasarela 8000:8000 & kubectl port-forward svc/keycloak 8180:8080 &
```

La imagen del nodo se fija en Kubernetes 1.33 porque las versiones nuevas no arrancan con cgroup v1
(el de Docker Desktop sobre WSL 2 sin actualizar). Probado así: los 11 pods listos sin ningún
reinicio y las 8 comprobaciones de `herramientas/prueba-seguridad.mjs` bien, por la pasarela y con
tokens de Keycloak (con `port-forward` también a los puertos 8080 a 8084, que el script comprueba).

Antes de arrancar, cada servicio espera a PostgreSQL y crea su base si falta (un contenedor previo
con `pg_isready` y `psql`). En local las bases ya existen; en AWS las crea ese paso.

## AWS

[`despliegue/terraform`](despliegue/terraform) crea la red, un clúster EKS, PostgreSQL en RDS y un
repositorio de ECR por servicio; el resto (Kafka, Keycloak, Grafana, Mailpit) va con el mismo chart
dentro del clúster ([decisión 0009](docs/decisiones/0009-aws-con-terraform.md)). Es para levantar la
demo, grabarla y **apagarla**: sin NAT ni balanceador, sin copias de la base de datos, y todo se
borra con `terraform destroy`. En CI solo se comprueba (`terraform fmt` y `validate`).

Hacen falta una cuenta de AWS con credenciales en la terminal, la AWS CLI, `terraform`, `kubectl` y
`helm`. Antes, comprueba que la versión de `version_kubernetes` está en soporte estándar
(`aws eks describe-cluster-versions`) y mira el coste en la calculadora de AWS: casi todo es el
clúster EKS, los dos nodos y RDS, y se cobra por hora.

Con `aws login`, las credenciales duran unos 15 minutos y Terraform no las renueva solo: si caducan
a mitad de `apply`, lo creado queda marcado como «tainted». Se le quita la marca
(`terraform untaint …`, para no borrar y recrear el clúster) y se vuelve a lanzar con credenciales
nuevas: `eval "$(aws configure export-credentials --format env)"`.

```sh
cd despliegue/terraform
terraform init && terraform apply
aws eks update-kubeconfig --name "$(terraform output -raw cluster)"
REGISTRO=$(terraform output -raw registro)
terraform output -raw valores_helm > aws.values.yaml
cd ../..

aws ecr get-login-password | docker login --username AWS --password-stdin "$REGISTRO"
for s in servicio-encaje servicio-recogida servicio-lectura servicio-avisos servicio-preguntas pasarela; do
  docker tag plataforma-$s "$REGISTRO/subvenciones/$s:demo" && docker push "$REGISTRO/subvenciones/$s:demo"
done
helm install subvenciones despliegue/helm/subvenciones -f despliegue/terraform/aws.values.yaml \
  --set secretos.baseDeDatos="$(terraform -chdir=despliegue/terraform output -raw contrasena_base_de_datos)" \
  --wait --timeout 15m
kubectl port-forward svc/pasarela 8000:8000 & kubectl port-forward svc/keycloak 8180:8080 &

# Al acabar, siempre:
helm uninstall subvenciones
terraform -chdir=despliegue/terraform destroy
```

Escrito y validado, **aún no aplicado** contra una cuenta de AWS.

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
| Grafana | 3000 | trazas (Tempo), métricas (Prometheus) y logs |
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
- [0008 Observabilidad con OpenTelemetry, y trazas que cruzan la outbox](docs/decisiones/0008-observabilidad.md)
- [0009 AWS con Terraform: EKS y RDS, el resto con el mismo chart](docs/decisiones/0009-aws-con-terraform.md)
