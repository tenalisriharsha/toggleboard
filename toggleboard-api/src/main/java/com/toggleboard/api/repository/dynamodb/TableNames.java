package com.toggleboard.api.repository.dynamodb;

/** Table and GSI names for the DynamoDB-backed repositories. */
final class TableNames {

    static final String ENVIRONMENTS = "toggleboard_environments";
    static final String FLAGS = "toggleboard_flags";
    static final String SEGMENTS = "toggleboard_segments";
    static final String AUDIT_ENTRIES = "toggleboard_audit_entries";

    static final String API_KEY_INDEX = "ApiKeyIndex";
    static final String ENVIRONMENT_KEY_INDEX = "EnvironmentKeyIndex";

    private TableNames() {
    }
}
