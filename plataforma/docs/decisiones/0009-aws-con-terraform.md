# 0009 AWS con Terraform: EKS y RDS, el resto con el mismo chart

- Estado: aceptada
- Fecha: 2026-11-11

## Contexto

La plataforma ya se despliega en Kubernetes local con un chart de Helm. Falta enseñarla en una nube
de verdad, pero el radar se mantiene a coste cero: la demo en AWS se levanta, se graba y se apaga.
Así que cada pieza tiene que justificarse por lo que enseña frente a lo que cuesta por hora, y todo
tiene que poder borrarse con un solo `terraform destroy`.

## Decisión

- **Terraform** (`despliegue/terraform`) crea la red, el clúster, la base de datos y los registros
  de imágenes. Recursos de AWS escritos directamente, sin módulos de terceros: se lee entero y cada
  pieza está a la vista. Estado en local: es la demo de una persona.
- **EKS** con un grupo de nodos gestionado (2 × `m7i-flex.large`, 8 GiB). Con el plan gratuito de
  AWS solo se pueden lanzar instancias aptas para la capa gratuita, y de ellas es la única con
  memoria suficiente; `t3.large` se queda sin nodos (EKS no da error: el grupo se queda en
  «creando» y el motivo solo aparece en CloudTrail). `upgrade_policy` en `STANDARD`: el
  clúster nunca pasa solo al soporte ampliado, que se cobra aparte.
- **RDS PostgreSQL 17** (`db.t4g.micro`) con una base por servicio, como en local. pgvector viene
  incluido. Sin IP pública: solo se entra desde el grupo de seguridad del clúster. Sin copias ni
  protección de borrado, para que `destroy` no deje nada atrás.
- **Kafka, Keycloak, Grafana y Mailpit siguen dentro del clúster**, con el mismo chart
  (`infra.postgres=false` quita solo PostgreSQL). MSK, Cognito o Amazon Managed Grafana serían lo
  propio en producción, pero cuestan por hora bastante más que todo lo demás junto y no cambian
  nada del código: los servicios solo reciben direcciones por variables de entorno.
- **Sin NAT Gateway ni balanceador:** cobran por hora aunque no se usen. Los nodos van en subredes
  públicas (para bajar imágenes) y no hay ningún Service expuesto: se entra con
  `kubectl port-forward`, como en kind. Así el emisor de los tokens de Keycloak es el mismo que en
  local.
- **ECR**, un repositorio por servicio, con `force_delete` y solo las tres últimas imágenes.
- **Las bases las crea el chart:** el contenedor previo de cada servicio espera a PostgreSQL y crea
  su base si falta. En local no hace nada (las crea el script del primer arranque); en RDS, sí. De
  paso, los servicios ya no se reinician mientras esperan a la base de datos.

## Consecuencias

- Se puede levantar y tirar en unos 20 minutos, y no queda nada encendido.
- No es un despliegue de producción: una sola zona para RDS, Kafka sin réplicas ni volúmenes,
  Keycloak en modo desarrollo y sin entrada pública con TLS. Para producción: subredes privadas
  con NAT, MSK, RDS multi-AZ con copias, un Ingress con certificado y el estado de Terraform en S3.
- En CI solo se comprueba (`fmt`, `validate`): aplicar necesita una cuenta de AWS y cuesta dinero.
