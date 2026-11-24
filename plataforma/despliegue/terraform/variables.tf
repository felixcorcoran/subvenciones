variable "region" {
  description = "Región de AWS"
  type        = string
  default     = "eu-west-1"
}

variable "nombre" {
  description = "Prefijo de los nombres de todo lo que se crea"
  type        = string
  default     = "subvenciones"
}

variable "version_kubernetes" {
  description = "Versión de Kubernetes de EKS. Tiene que estar en soporte estándar (aws eks describe-cluster-versions): el soporte ampliado cuesta bastante más por hora"
  type        = string
  default     = "1.35"
}

variable "tipo_nodo" {
  description = "Tipo de instancia de los nodos. Los seis servicios, Kafka, Keycloak y Grafana piden unos 6 GB de memoria. Con el plan gratuito de AWS solo se pueden lanzar tipos aptos para la capa gratuita (aws ec2 describe-instance-types --filters Name=free-tier-eligible,Values=true): de ellos, m7i-flex.large es el único con 8 GiB"
  type        = string
  default     = "m7i-flex.large"
}

variable "numero_nodos" {
  description = "Número de nodos del clúster"
  type        = number
  default     = 2

  validation {
    condition     = var.numero_nodos >= 1 && var.numero_nodos <= 3
    error_message = "Para la demo, entre 1 y 3 nodos."
  }
}

variable "clase_base_de_datos" {
  description = "Clase de la instancia de RDS"
  type        = string
  default     = "db.t4g.micro"
}

variable "cidr_red" {
  description = "Rango de direcciones de la VPC"
  type        = string
  default     = "10.40.0.0/16"
}

variable "acceso_api_kubernetes" {
  description = "Rangos que pueden llegar a la API de Kubernetes (por ejemplo, tu IP con /32)"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
