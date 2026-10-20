variable "aws_region" {
  description = "AWS region to provision toggleboard into."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short name used as a prefix for every resource this module creates."
  type        = string
  default     = "toggleboard"
}

variable "environment" {
  description = "Deployment environment label (e.g. staging, production), used only for tagging."
  type        = string
  default     = "staging"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC created for the ECS service and ALB."
  type        = string
  default     = "10.42.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones to spread the two public subnets across."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "container_image" {
  description = "Container image for toggleboard-api, e.g. <account>.dkr.ecr.<region>.amazonaws.com/toggleboard-api:latest. A placeholder here is fine until a real image is pushed."
  type        = string
  default     = "toggleboard-api:local"
}

variable "container_port" {
  description = "Port toggleboard-api listens on inside the container."
  type        = number
  default     = 8080
}

variable "task_cpu" {
  description = "Fargate task CPU units (256 = 0.25 vCPU)."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory in MiB."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Number of toggleboard-api tasks to run."
  type        = number
  default     = 2
}

variable "dynamodb_profile_env" {
  description = "Spring profile passed to the container so it talks to the real DynamoDB tables this module creates."
  type        = string
  default     = "dynamodb"
}

variable "console_index_document" {
  description = "Index document CloudFront/S3 serve for the Angular console's SPA routing."
  type        = string
  default     = "index.html"
}

variable "tags" {
  description = "Common tags applied to every resource."
  type        = map(string)
  default     = {}
}
