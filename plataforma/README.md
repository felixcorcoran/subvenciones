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
| 2. Servicios y eventos | Kafka con idempotencia y DLT (hecho en el encaje); recogida, lectura con IA y avisos como servicios; outbox | **en marcha** |
| 3. IA medida | «Pregunta a la convocatoria» (RAG con citas) y evaluación con datos en CI | pendiente |
| 4. Seguridad | OAuth2 / OIDC con Keycloak, Spring Security, gateway | pendiente |
| 5. Operación | Kubernetes (kind + Helm), OpenTelemetry + Grafana, AWS con Terraform | pendiente |

## Hacia dónde va

```
BDNS ─► recogida ─(convocatoria.publicada)─► lectura IA ─(convocatoria.leida)─► encaje ─(encaje.encontrado)─► avisos ─► correo
                                                  │                                │
                                       pregunta a la convocatoria (RAG)     suscripciones (OAuth2)
          PostgreSQL (+ pgvector) · Kafka (KRaft) · OpenTelemetry → Grafana · Keycloak
```

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
docker compose up --build     # PostgreSQL + Kafka + servicio-encaje en :8080
```

Para cargar las convocatorias reales de la web como eventos (en Git Bash, con
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
