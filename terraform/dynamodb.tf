# Table/GSI shapes here must stay in lockstep with
# toggleboard-api's DynamoDbTableInitializer (names, keys and indexes),
# since the app's own ApplicationRunner also creates these tables
# idempotently if they're missing. Terraform owns the resources so they're
# tracked as infrastructure (and torn down with everything else); the app's
# initializer is what still makes `./mvnw -Dspring.profiles.active=dynamodb`
# work against a fresh account with no Terraform run yet.

resource "aws_dynamodb_table" "environments" {
  name         = "toggleboard_environments"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "key"

  attribute {
    name = "key"
    type = "S"
  }

  attribute {
    name = "apiKey"
    type = "S"
  }

  global_secondary_index {
    name            = "ApiKeyIndex"
    hash_key        = "apiKey"
    projection_type = "ALL"
  }

  tags = merge(local.common_tags, {
    Name = "toggleboard_environments"
  })
}

resource "aws_dynamodb_table" "flags" {
  name         = "toggleboard_flags"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "environmentKey"
    type = "S"
  }

  attribute {
    name = "key"
    type = "S"
  }

  global_secondary_index {
    name            = "EnvironmentKeyIndex"
    hash_key        = "environmentKey"
    range_key       = "key"
    projection_type = "ALL"
  }

  tags = merge(local.common_tags, {
    Name = "toggleboard_flags"
  })
}

resource "aws_dynamodb_table" "segments" {
  name         = "toggleboard_segments"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "environmentKey"
    type = "S"
  }

  attribute {
    name = "key"
    type = "S"
  }

  global_secondary_index {
    name            = "EnvironmentKeyIndex"
    hash_key        = "environmentKey"
    range_key       = "key"
    projection_type = "ALL"
  }

  tags = merge(local.common_tags, {
    Name = "toggleboard_segments"
  })
}

resource "aws_dynamodb_table" "audit_entries" {
  name         = "toggleboard_audit_entries"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "environmentKey"
  range_key    = "sortKey"

  attribute {
    name = "environmentKey"
    type = "S"
  }

  attribute {
    name = "sortKey"
    type = "S"
  }

  tags = merge(local.common_tags, {
    Name = "toggleboard_audit_entries"
  })
}
