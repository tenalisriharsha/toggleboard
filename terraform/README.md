# toggleboard — Terraform

Provisions toggleboard on AWS: ECS Fargate running `toggleboard-api`, the four
DynamoDB tables it needs, an ALB in front of the service, and S3 + CloudFront
serving `toggleboard-console`'s static build.

**No real AWS account exists in this project's environment** — only dummy
credentials. This module is validated with `terraform fmt`/`validate` and a
`terraform test` suite against `mock_provider`; it has never been applied
against a real account from here.

## Layout

| File               | Provisions |
|--------------------|------------|
| `vpc.tf`           | VPC, internet gateway, two public subnets (one per AZ), routing |
| `dynamodb.tf`      | The four tables (`environments`, `flags`, `segments`, `audit_entries`), matching `DynamoDbTableInitializer`'s schema exactly |
| `ecs.tf`           | ECS cluster, Fargate task definition + service, IAM execution/task roles (task role scoped to only these four tables), security group, CloudWatch log group |
| `alb.tf`           | Internet-facing ALB, HTTP listener, target group health-checking `GET /actuator/health` |
| `cloudfront.tf`    | Private S3 bucket + CloudFront distribution (Origin Access Control) for the console, with SPA deep-link fallback to `index.html` |
| `variables.tf`     | Every input, with sensible defaults so `terraform validate`/`test` need no `-var` flags |
| `outputs.tf`       | ALB DNS name, console CloudFront domain, console bucket name, DynamoDB table names |

## Validating

```
cd terraform
terraform init
terraform fmt -check -recursive
terraform validate
terraform test          # runs tests/*.tftest.hcl against mock_provider
```

All four `tests/*.tftest.hcl` files pass: `dynamodb.tftest.hcl` checks the
table/GSI shapes against the app's own schema, `ecs.tftest.hcl` checks the
Fargate service config and that the task role's DynamoDB policy is scoped to
exactly these four tables (not `"Resource": "*"`), `alb.tftest.hcl` checks the
listener/target-group/security-group config, and `cloudfront.tftest.hcl`
checks the bucket is private behind OAC and that 403/404 fall back to
`index.html` for the Angular router.

A handful of resources' `*_arn` attributes get `override_resource` blocks in
`tests/ecs.tftest.hcl`: `mock_provider`'s default synthetic value for a
computed ARN is just a random id, which fails the real AWS provider's own
ARN-format validation the moment one mocked resource's ARN is wired into
another (e.g. the task definition's `execution_role_arn`). The overrides give
those mocked resources realistic-looking ARNs so the one `command = apply`
run in that file can get past that validation without ever calling real AWS.

## Deploying for real (not done in this sprint)

1. Push a built `toggleboard-api` image somewhere `container_image` can
   reference (e.g. ECR) and set that variable.
2. `terraform apply` against a real account with real credentials.
3. `ng build` the console and sync `dist/toggleboard-console/browser/` to the
   bucket named in the `console_bucket_name` output, then invalidate the
   CloudFront distribution.
