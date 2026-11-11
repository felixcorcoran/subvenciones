# Un repositorio de imágenes por servicio: <registro>/subvenciones/<servicio>, que es la forma
# que espera el chart (imagen.registro = <registro>/subvenciones).

locals {
  servicios = toset([
    "servicio-encaje",
    "servicio-recogida",
    "servicio-lectura",
    "servicio-avisos",
    "servicio-preguntas",
    "pasarela",
  ])
}

resource "aws_ecr_repository" "servicio" {
  for_each = local.servicios

  name                 = "${var.nombre}/${each.key}"
  image_tag_mutability = "MUTABLE"
  # terraform destroy lo borra aunque tenga imágenes
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Solo se guardan las tres últimas imágenes de cada servicio
resource "aws_ecr_lifecycle_policy" "servicio" {
  for_each = aws_ecr_repository.servicio

  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Las tres ultimas imagenes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 3
      }
      action = { type = "expire" }
    }]
  })
}
