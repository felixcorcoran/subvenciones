# El clúster EKS y un grupo de nodos gestionado. Kafka, Keycloak, Grafana y Mailpit van dentro
# del clúster, con el mismo chart que en local (ver la decisión 0009).

resource "aws_iam_role" "cluster" {
  name = "${var.nombre}-eks-cluster"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = ["sts:AssumeRole", "sts:TagSession"]
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_eks_cluster" "principal" {
  name     = var.nombre
  version  = var.version_kubernetes
  role_arn = aws_iam_role.cluster.arn

  vpc_config {
    subnet_ids              = aws_subnet.publica[*].id
    endpoint_public_access  = true
    endpoint_private_access = true
    public_access_cidrs     = var.acceso_api_kubernetes
  }

  # Permisos por entradas de acceso de EKS; quien crea el clúster queda como administrador
  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = true
  }

  # Que nunca pase solo al soporte ampliado, que se cobra aparte
  upgrade_policy {
    support_type = "STANDARD"
  }

  depends_on = [aws_iam_role_policy_attachment.cluster]
}

resource "aws_iam_role" "nodos" {
  name = "${var.nombre}-eks-nodos"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "nodos" {
  for_each = toset([
    "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy",
    # Para bajar las imágenes de los servicios de ECR
    "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
  ])

  role       = aws_iam_role.nodos.name
  policy_arn = each.value
}

resource "aws_eks_node_group" "principal" {
  cluster_name    = aws_eks_cluster.principal.name
  node_group_name = "principal"
  node_role_arn   = aws_iam_role.nodos.arn
  subnet_ids      = aws_subnet.publica[*].id

  # Las imágenes se construyen para x86_64
  ami_type       = "AL2023_x86_64_STANDARD"
  capacity_type  = "ON_DEMAND"
  instance_types = [var.tipo_nodo]
  disk_size      = 30

  scaling_config {
    desired_size = var.numero_nodos
    min_size     = var.numero_nodos
    max_size     = var.numero_nodos
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [aws_iam_role_policy_attachment.nodos]
}
