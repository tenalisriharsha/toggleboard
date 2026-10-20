resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name_prefix}-api"
  retention_in_days = 14

  tags = local.common_tags
}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.common_tags
}

locals {
  # A static document (no reference to any resource attribute), expressed
  # directly with jsonencode rather than an aws_iam_policy_document data
  # source: in `terraform test` with mock_provider, data sources are mocked
  # too and would return a synthetic, non-JSON "json" attribute, which then
  # fails the assume_role_policy argument's own JSON validation at plan
  # time. jsonencode is a pure Terraform-language function, not a provider
  # call, so it stays correct under mocking.
  ecs_task_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Action    = "sts:AssumeRole"
        Principal = { Service = "ecs-tasks.amazonaws.com" }
      }
    ]
  })
}

resource "aws_iam_role" "ecs_execution" {
  name               = "${local.name_prefix}-ecs-execution"
  assume_role_policy = local.ecs_task_assume_role_policy
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# The application role the running container assumes: scoped to exactly the
# DynamoDB tables and indexes this service reads/writes, nothing broader.
resource "aws_iam_role" "ecs_task" {
  name               = "${local.name_prefix}-ecs-task"
  assume_role_policy = local.ecs_task_assume_role_policy
  tags               = local.common_tags
}

resource "aws_iam_role_policy" "ecs_task_dynamodb" {
  name = "${local.name_prefix}-dynamodb-access"
  role = aws_iam_role.ecs_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:DescribeTable",
          "dynamodb:CreateTable",
        ]
        Resource = [
          aws_dynamodb_table.environments.arn,
          "${aws_dynamodb_table.environments.arn}/index/*",
          aws_dynamodb_table.flags.arn,
          "${aws_dynamodb_table.flags.arn}/index/*",
          aws_dynamodb_table.segments.arn,
          "${aws_dynamodb_table.segments.arn}/index/*",
          aws_dynamodb_table.audit_entries.arn,
          "${aws_dynamodb_table.audit_entries.arn}/index/*",
        ]
      }
    ]
  })
}

resource "aws_security_group" "api" {
  name        = "${local.name_prefix}-api-sg"
  description = "toggleboard-api Fargate tasks: inbound only from the ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From the ALB"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "All outbound (DynamoDB, etc.)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-api-sg"
  })
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = "toggleboard-api"
      image     = var.container_image
      essential = true
      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]
      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = var.dynamodb_profile_env
        },
        {
          name  = "AWS_REGION"
          value = var.aws_region
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.api.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "api"
        }
      }
    }
  ])

  tags = local.common_tags
}

resource "aws_ecs_service" "api" {
  name            = "${local.name_prefix}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.api.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "toggleboard-api"
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener.api]

  tags = local.common_tags
}
