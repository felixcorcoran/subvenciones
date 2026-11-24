locals {
  registro = split("/", aws_ecr_repository.servicio["pasarela"].repository_url)[0]
}

output "cluster" {
  description = "Nombre del clúster (aws eks update-kubeconfig --name …)"
  value       = aws_eks_cluster.principal.name
}

output "registro" {
  description = "Registro de ECR, para docker login"
  value       = local.registro
}

output "contrasena_base_de_datos" {
  description = "Contraseña de RDS, para helm install --set secretos.baseDeDatos=…"
  value       = random_password.base_de_datos.result
  sensitive   = true
}

# Los valores del chart para AWS (sin secretos): terraform output -raw valores_helm > aws.values.yaml
output "valores_helm" {
  description = "Valores del chart de Helm para este despliegue"
  value = yamlencode({
    imagen = {
      registro = "${local.registro}/${var.nombre}"
      etiqueta = "demo"
      politica = "Always"
    }
    comun = {
      baseDeDatos = {
        servidor = "${aws_db_instance.principal.address}:${aws_db_instance.principal.port}"
        usuario  = aws_db_instance.principal.username
      }
    }
    infra = {
      activada = true
      postgres = false
    }
  })
}
