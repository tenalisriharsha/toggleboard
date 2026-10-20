mock_provider "aws" {}

run "console_bucket_is_private_behind_cloudfront_oac" {
  command = plan

  assert {
    condition     = aws_s3_bucket_public_access_block.console.block_public_acls == true
    error_message = "the console bucket must block public ACLs: CloudFront OAC is the only intended path in"
  }

  assert {
    condition     = aws_cloudfront_origin_access_control.console.signing_behavior == "always"
    error_message = "OAC must always sign requests to the S3 origin"
  }

  assert {
    condition     = aws_cloudfront_distribution.console.default_root_object == "index.html"
    error_message = "default root object must be index.html"
  }
}

run "spa_deep_links_fall_back_to_index_html" {
  command = plan

  assert {
    condition = anytrue([
      for r in aws_cloudfront_distribution.console.custom_error_response :
      r.error_code == 404 && r.response_code == 200 && r.response_page_path == "/index.html"
    ])
    error_message = "a 404 from S3 (unknown Angular route) must be rewritten to a 200 serving index.html"
  }

  assert {
    condition = anytrue([
      for r in aws_cloudfront_distribution.console.custom_error_response :
      r.error_code == 403 && r.response_code == 200 && r.response_page_path == "/index.html"
    ])
    error_message = "a 403 from S3 (unknown Angular route via OAC) must be rewritten to a 200 serving index.html"
  }
}
