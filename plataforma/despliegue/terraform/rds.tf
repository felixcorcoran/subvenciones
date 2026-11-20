# PostgreSQL gestionado. Una sola instancia con una base por servicio, como en local; las bases
# las crea el contenedor previo de cada servicio en el chart. pgvector viene incluido en RDS
# (la migración de servicio-preguntas hace CREATE EXTENSION vector).

resource "random_password" "base_de_datos" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "principal" {
  name       = var.nombre
  subnet_ids = aws_subnet.publica[*].id
}

# Solo se entra desde el clúster: los nodos gestionados usan el grupo de seguridad del clúster
resource "aws_security_group" "base_de_datos" {
  name        = "${var.nombre}-base-de-datos"
  description = "PostgreSQL, solo desde el cluster EKS"
  vpc_id      = aws_vpc.principal.id

  tags = { Name = "${var.nombre}-base-de-datos" }
}

resource "aws_vpc_security_group_ingress_rule" "base_de_datos_desde_cluster" {
  security_group_id            = aws_security_group.base_de_datos.id
  referenced_security_group_id = aws_eks_cluster.principal.vpc_config[0].cluster_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_db_instance" "principal" {
  identifier     = var.nombre
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.clase_base_de_datos

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  # La base de servicio-encaje; las demás las crea el chart
  db_name  = "subvenciones"
  username = "subvenciones"
  password = random_password.base_de_datos.result

  db_subnet_group_name   = aws_db_subnet_group.principal.name
  vpc_security_group_ids = [aws_security_group.base_de_datos.id]
  publicly_accessible    = false
  multi_az               = false

  # Es una demo: se borra entera con terraform destroy, sin copia final
  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true
}
