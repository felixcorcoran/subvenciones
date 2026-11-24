# Una VPC con dos subredes públicas en dos zonas (EKS y RDS piden al menos dos).
# Sin NAT Gateway: cobra por hora aunque no se use. Los nodos tienen IP pública para bajar
# imágenes; desde fuera no se entra (sin Service LoadBalancer: se llega con kubectl port-forward).
# RDS va en las mismas subredes, pero sin IP pública y solo abierto al clúster.

locals {
  zonas = slice(data.aws_availability_zones.disponibles.names, 0, 2)
}

resource "aws_vpc" "principal" {
  cidr_block           = var.cidr_red
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = var.nombre }
}

resource "aws_internet_gateway" "principal" {
  vpc_id = aws_vpc.principal.id

  tags = { Name = var.nombre }
}

resource "aws_subnet" "publica" {
  count = length(local.zonas)

  vpc_id                  = aws_vpc.principal.id
  availability_zone       = local.zonas[count.index]
  cidr_block              = cidrsubnet(var.cidr_red, 4, count.index)
  map_public_ip_on_launch = true

  tags = {
    Name                     = "${var.nombre}-publica-${local.zonas[count.index]}"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_route_table" "publica" {
  vpc_id = aws_vpc.principal.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.principal.id
  }

  tags = { Name = "${var.nombre}-publica" }
}

resource "aws_route_table_association" "publica" {
  count = length(aws_subnet.publica)

  subnet_id      = aws_subnet.publica[count.index].id
  route_table_id = aws_route_table.publica.id
}
