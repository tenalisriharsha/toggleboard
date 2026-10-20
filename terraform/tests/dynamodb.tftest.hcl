# mock_provider stands in for the real AWS provider: every resource is
# "created" against a synthetic in-memory fake that never calls AWS, so this
# suite runs with only the dummy credentials present in this environment and
# proves the plan/apply graph is correct without touching a real account.
mock_provider "aws" {}

run "dynamodb_tables_match_the_app_schema" {
  command = plan

  assert {
    condition     = aws_dynamodb_table.environments.hash_key == "key"
    error_message = "environments table must be keyed on 'key' to match DynamoDbTableInitializer"
  }

  assert {
    condition     = aws_dynamodb_table.environments.billing_mode == "PAY_PER_REQUEST"
    error_message = "all tables should be on-demand, no provisioned capacity to manage"
  }

  assert {
    condition     = one(aws_dynamodb_table.environments.global_secondary_index[*].name) == "ApiKeyIndex"
    error_message = "environments table must expose ApiKeyIndex on apiKey"
  }

  assert {
    condition     = aws_dynamodb_table.flags.hash_key == "id"
    error_message = "flags table must be keyed on 'id' to match DynamoDbTableInitializer"
  }

  assert {
    condition     = one(aws_dynamodb_table.flags.global_secondary_index[*].range_key) == "key"
    error_message = "flags table's EnvironmentKeyIndex must range on 'key' for sorted per-environment lookups"
  }

  assert {
    condition     = aws_dynamodb_table.segments.hash_key == "id"
    error_message = "segments table must be keyed on 'id' to match DynamoDbTableInitializer"
  }

  assert {
    condition     = aws_dynamodb_table.audit_entries.hash_key == "environmentKey" && aws_dynamodb_table.audit_entries.range_key == "sortKey"
    error_message = "audit_entries table must be keyed on (environmentKey, sortKey) for a newest-first query with no extra GSI"
  }

  assert {
    condition     = length(aws_dynamodb_table.audit_entries.global_secondary_index) == 0
    error_message = "audit_entries intentionally has no GSI: the sort key alone gives newest-first ordering"
  }
}
