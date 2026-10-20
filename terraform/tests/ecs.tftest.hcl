mock_provider "aws" {}

# The AWS provider validates that *_role_arn / *_arn arguments look like a
# real ARN even when every resource producing them is mocked; mock_provider's
# default synthetic value for a computed "arn" attribute is just a random
# id, which fails that validation the moment a run applies far enough to
# wire one mocked resource's arn into another. These overrides give the
# mocked resources realistic-looking ARNs so `command = apply` runs below
# can get past that validation without ever calling real AWS.
override_resource {
  target = aws_iam_role.ecs_execution
  values = {
    arn = "arn:aws:iam::123456789012:role/toggleboard-staging-ecs-execution"
  }
}

override_resource {
  target = aws_iam_role.ecs_task
  values = {
    arn = "arn:aws:iam::123456789012:role/toggleboard-staging-ecs-task"
  }
}

override_resource {
  target = aws_lb.api
  values = {
    arn      = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/toggleboard-staging-alb/1234567890abcdef"
    dns_name = "toggleboard-staging-alb-1234567890.us-east-1.elb.amazonaws.com"
    zone_id  = "Z35SXDOTRQ7X7K"
  }
}

override_resource {
  target = aws_lb_target_group.api
  values = {
    arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/toggleboard-staging-api-tg/1234567890abcdef"
  }
}

override_resource {
  target = aws_dynamodb_table.environments
  values = {
    arn = "arn:aws:dynamodb:us-east-1:123456789012:table/toggleboard_environments"
  }
}

override_resource {
  target = aws_dynamodb_table.flags
  values = {
    arn = "arn:aws:dynamodb:us-east-1:123456789012:table/toggleboard_flags"
  }
}

override_resource {
  target = aws_dynamodb_table.segments
  values = {
    arn = "arn:aws:dynamodb:us-east-1:123456789012:table/toggleboard_segments"
  }
}

override_resource {
  target = aws_dynamodb_table.audit_entries
  values = {
    arn = "arn:aws:dynamodb:us-east-1:123456789012:table/toggleboard_audit_entries"
  }
}

run "ecs_service_is_fargate_and_sits_behind_the_alb" {
  command = plan

  assert {
    condition     = contains(aws_ecs_task_definition.api.requires_compatibilities, "FARGATE")
    error_message = "task definition must require FARGATE, no EC2 capacity to manage"
  }

  assert {
    condition     = aws_ecs_task_definition.api.network_mode == "awsvpc"
    error_message = "FARGATE requires awsvpc networking mode"
  }

  assert {
    condition     = aws_ecs_service.api.launch_type == "FARGATE"
    error_message = "service must launch on FARGATE"
  }

  assert {
    condition     = aws_ecs_service.api.desired_count == var.desired_count
    error_message = "service desired_count must track the desired_count variable"
  }
}

run "api_security_group_only_admits_the_alb" {
  command = plan

  assert {
    condition     = anytrue([for rule in aws_security_group.api.ingress : length(rule.security_groups) > 0])
    error_message = "the api security group must restrict ingress to a security group (the ALB's), not an open CIDR"
  }

  assert {
    condition     = alltrue([for rule in aws_security_group.api.ingress : try(length(rule.cidr_blocks), 0) == 0])
    error_message = "the api security group must not accept inbound traffic from arbitrary CIDRs"
  }
}

run "task_role_is_scoped_to_only_the_toggleboard_tables" {
  # apply, not plan: the policy JSON interpolates DynamoDB table ARNs, which
  # mock_provider leaves unknown until apply (same as real AWS would).
  command = apply

  assert {
    condition     = can(regex("toggleboard_environments", aws_iam_role_policy.ecs_task_dynamodb.policy))
    error_message = "the task's DynamoDB policy must reference the environments table"
  }

  assert {
    condition     = !can(regex("\"Resource\":\\s*\"\\*\"", aws_iam_role_policy.ecs_task_dynamodb.policy))
    error_message = "the task's DynamoDB policy must not grant access to all resources (*)"
  }
}
