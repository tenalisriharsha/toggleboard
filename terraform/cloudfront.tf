# Serves toggleboard-console's static build (ng build's dist/ output,
# uploaded separately by the deploy pipeline) from a private S3 bucket
# fronted by CloudFront via Origin Access Control — the bucket itself never
# needs to be public.

resource "aws_s3_bucket" "console" {
  bucket = "${local.name_prefix}-console"

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-console"
  })
}

resource "aws_s3_bucket_public_access_block" "console" {
  bucket = aws_s3_bucket.console.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "console" {
  name                              = "${local.name_prefix}-console-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "console" {
  enabled             = true
  default_root_object = var.console_index_document
  comment             = "${local.name_prefix} admin console"

  origin {
    domain_name              = aws_s3_bucket.console.bucket_regional_domain_name
    origin_id                = "console-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.console.id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "console-s3"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  # The Angular console is a client-side-routed SPA: an unknown deep link
  # (e.g. a page refresh on /flags/123/edit) has no matching S3 object, so
  # CloudFront must fall back to index.html and let Angular's router take it
  # from there instead of surfacing S3's raw 403/404.
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/${var.console_index_document}"
  }

  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/${var.console_index_document}"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = local.common_tags
}

resource "aws_s3_bucket_policy" "console" {
  bucket = aws_s3_bucket.console.id
  # jsonencode, not an aws_iam_policy_document data source: see the
  # comment on local.ecs_task_assume_role_policy in ecs.tf for why.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowCloudFrontRead"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.console.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.console.arn
          }
        }
      }
    ]
  })
}
