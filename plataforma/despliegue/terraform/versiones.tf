# La demo en AWS: se levanta, se graba y se apaga (terraform destroy). Nada se queda encendido.
# El estado se guarda en local (terraform.tfstate, fuera de Git): para una demo de una persona no
# hace falta un backend en S3.

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.region

  # Todo lo que se crea lleva estas etiquetas: así se encuentra en la consola y en la factura
  default_tags {
    tags = {
      Proyecto  = "subvenciones"
      Entorno   = "demo"
      Terraform = "true"
    }
  }
}

data "aws_availability_zones" "disponibles" {
  state = "available"
}
