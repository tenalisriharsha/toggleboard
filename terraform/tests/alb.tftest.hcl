mock_provider "aws" {}

run "alb_is_internet_facing_http_only_with_a_health_checked_target_group" {
  command = plan

  assert {
    condition     = aws_lb.api.internal == false
    error_message = "the ALB must be internet-facing so the console/clients can reach toggleboard-api"
  }

  assert {
    condition     = aws_lb.api.load_balancer_type == "application"
    error_message = "must be an ALB (application), not an NLB/GWLB"
  }

  assert {
    condition     = aws_lb_listener.api.port == 80 && aws_lb_listener.api.protocol == "HTTP"
    error_message = "listener should be plain HTTP on port 80 (no ACM cert provisioned in this sprint)"
  }

  assert {
    condition     = aws_lb_target_group.api.target_type == "ip"
    error_message = "Fargate tasks are registered by IP, not instance, in the target group"
  }

  assert {
    condition     = aws_lb_target_group.api.health_check[0].path == "/actuator/health"
    error_message = "target group health check must hit the app's real health endpoint"
  }
}

run "alb_security_group_only_opens_port_80" {
  command = plan

  assert {
    condition     = anytrue([for rule in aws_security_group.alb.ingress : rule.from_port == 80 && rule.to_port == 80])
    error_message = "the ALB security group should only open port 80"
  }
}
