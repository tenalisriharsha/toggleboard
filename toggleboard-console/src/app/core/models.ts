export type FlagType = 'BOOLEAN' | 'MULTIVARIATE';

export type TargetingOperator = 'EQUALS' | 'IN' | 'CONTAINS';

export type AuditAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'KILL_SWITCH_ENABLED' | 'KILL_SWITCH_DISABLED';

export type AuditEntityType = 'FLAG' | 'ENVIRONMENT' | 'SEGMENT';

export interface ToggleboardEnvironment {
  key: string;
  name: string;
  apiKey: string;
  createdAt: string;
}

export interface Variant {
  key: string;
  value: string;
  weight: number;
}

export interface TargetingRule {
  attribute: string;
  operator: TargetingOperator;
  values: string[];
  overrideVariantKey: string | null;
}

export interface Flag {
  id: string;
  key: string;
  environmentKey: string;
  name: string;
  description: string | null;
  type: FlagType;
  enabled: boolean;
  rolloutPercentage: number;
  variants: Variant[];
  defaultVariantKey: string | null;
  targetingRules: TargetingRule[];
  segmentKeys: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Segment {
  id: string;
  environmentKey: string;
  key: string;
  name: string;
  description: string | null;
  rules: TargetingRule[];
  createdAt: string;
  updatedAt: string;
}

export interface AuditEntry {
  id: string;
  environmentKey: string;
  entityType: AuditEntityType;
  entityKey: string;
  action: AuditAction;
  actor: string | null;
  timestamp: string;
  before: string | null;
  after: string | null;
}

export interface CreateFlagRequest {
  key: string;
  name: string;
  description: string | null;
  type: FlagType;
  enabled: boolean;
  rolloutPercentage: number;
  variants: Variant[];
  defaultVariantKey: string | null;
  targetingRules: TargetingRule[];
  segmentKeys: string[];
}

export type UpdateFlagRequest = Omit<CreateFlagRequest, 'key' | 'type'>;

export interface CreateSegmentRequest {
  key: string;
  name: string;
  description: string | null;
  rules: TargetingRule[];
}

export type UpdateSegmentRequest = Omit<CreateSegmentRequest, 'key'>;
