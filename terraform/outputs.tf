output "alb_dns_name" {
  description = "Public DNS name of the ALB in front of toggleboard-api."
  value       = aws_lb.api.dns_name
}

output "ecs_cluster_name" {
  description = "Name of the ECS cluster running toggleboard-api."
  value       = aws_ecs_cluster.main.name
}

output "console_cloudfront_domain" {
  description = "CloudFront domain serving the Angular admin console."
  value       = aws_cloudfront_distribution.console.domain_name
}

output "console_bucket_name" {
  description = "S3 bucket to upload `ng build`'s dist/ output to."
  value       = aws_s3_bucket.console.bucket
}

output "dynamodb_table_names" {
  description = "Names of the four DynamoDB tables, matching DynamoDbTableInitializer's schema."
  value = {
    environments  = aws_dynamodb_table.environments.name
    flags         = aws_dynamodb_table.flags.name
    segments      = aws_dynamodb_table.segments.name
    audit_entries = aws_dynamodb_table.audit_entries.name
  }
}
