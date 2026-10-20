import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { NotificationService } from '../../core/notification.service';
import { apiErrorMessage } from '../../core/api-error';
import { CreateFlagRequest, Flag, FlagType, Segment, TargetingRule, Variant } from '../../core/models';
import { TargetingRulesEditorComponent } from '../../shared/targeting-rules-editor/targeting-rules-editor.component';

const KEY_PATTERN = /^[a-z0-9][a-z0-9-_]*$/;

interface FlagFormState {
  name: string;
  description: string;
  type: FlagType;
  enabled: boolean;
  rolloutPercentage: number;
  variants: Variant[];
  defaultVariantKey: string | null;
  targetingRules: TargetingRule[];
  segmentKeys: string[];
}

@Component({
  selector: 'app-flag-form',
  standalone: true,
  imports: [RouterLink, TargetingRulesEditorComponent],
  templateUrl: './flag-form.component.html',
  styleUrl: './flag-form.component.css',
})
export class FlagFormComponent implements OnInit {
  mode: 'create' | 'edit' = 'create';
  flagKey = '';
  loading = false;
  saving = false;
  allSegments: Segment[] = [];

  form: FlagFormState = {
    name: '',
    description: '',
    type: 'BOOLEAN',
    enabled: false,
    rolloutPercentage: 0,
    variants: [],
    defaultVariantKey: null,
    targetingRules: [],
    segmentKeys: [],
  };

  constructor(
    private readonly api: ApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    readonly context: EnvironmentContextService,
    private readonly notifications: NotificationService,
  ) {}

  ngOnInit(): void {
    const paramKey = this.route.snapshot.paramMap.get('flagKey');
    this.mode = paramKey ? 'edit' : 'create';
    this.flagKey = paramKey ?? '';

    const envKey = this.context.currentEnvironmentKey();
    if (!envKey) {
      return;
    }

    this.api.listSegments(envKey).subscribe({
      next: (segments) => (this.allSegments = segments),
      error: (err) => this.notifications.error(apiErrorMessage(err, 'Failed to load segments')),
    });

    if (this.mode === 'edit' && paramKey) {
      this.loading = true;
      this.api.getFlag(envKey, paramKey).subscribe({
        next: (flag) => {
          this.loading = false;
          this.applyFlag(flag);
        },
        error: (err) => {
          this.loading = false;
          this.notifications.error(apiErrorMessage(err, 'Failed to load flag'));
        },
      });
    }
  }

  private applyFlag(flag: Flag): void {
    this.form = {
      name: flag.name,
      description: flag.description ?? '',
      type: flag.type,
      enabled: flag.enabled,
      rolloutPercentage: flag.rolloutPercentage,
      variants: flag.variants.map((v) => ({ ...v })),
      defaultVariantKey: flag.defaultVariantKey,
      targetingRules: flag.targetingRules.map((r) => ({ ...r, values: [...r.values] })),
      segmentKeys: [...flag.segmentKeys],
    };
  }

  get variantWeightTotal(): number {
    return this.form.variants.reduce((sum, v) => sum + (Number(v.weight) || 0), 0);
  }

  get variantKeys(): string[] {
    return this.form.variants.map((v) => v.key).filter((k) => k.length > 0);
  }

  onTypeChange(type: FlagType): void {
    this.form.type = type;
    if (type === 'MULTIVARIATE' && this.form.variants.length === 0) {
      this.form.variants = [
        { key: 'on', value: 'true', weight: 50 },
        { key: 'off', value: 'false', weight: 50 },
      ];
      this.form.defaultVariantKey = 'off';
    }
  }

  addVariant(): void {
    this.form.variants = [...this.form.variants, { key: '', value: '', weight: 0 }];
  }

  removeVariant(index: number): void {
    const removedKey = this.form.variants[index]?.key;
    this.form.variants = this.form.variants.filter((_, i) => i !== index);
    if (this.form.defaultVariantKey === removedKey) {
      this.form.defaultVariantKey = this.form.variants[0]?.key ?? null;
    }
  }

  updateVariant(index: number, patch: Partial<Variant>): void {
    this.form.variants = this.form.variants.map((v, i) => (i === index ? { ...v, ...patch } : v));
  }

  toggleSegment(key: string): void {
    const has = this.form.segmentKeys.includes(key);
    this.form.segmentKeys = has ? this.form.segmentKeys.filter((k) => k !== key) : [...this.form.segmentKeys, key];
  }

  onRulesChange(rules: TargetingRule[]): void {
    this.form.targetingRules = rules;
  }

  private validate(): string | null {
    if (this.mode === 'create' && !KEY_PATTERN.test(this.flagKey)) {
      return 'Key must be lowercase, alphanumeric, "-" or "_", starting with a letter or digit';
    }
    if (!this.form.name.trim()) {
      return 'Name is required';
    }
    if (this.form.type === 'MULTIVARIATE') {
      if (this.form.variants.length === 0) {
        return 'Multivariate flags require at least one variant';
      }
      if (this.variantWeightTotal !== 100) {
        return `Variant weights must sum to 100 (currently ${this.variantWeightTotal})`;
      }
      if (!this.form.defaultVariantKey || !this.variantKeys.includes(this.form.defaultVariantKey)) {
        return 'Pick a default variant that matches one of the variant keys';
      }
    }
    return null;
  }

  save(): void {
    const envKey = this.context.currentEnvironmentKey();
    if (!envKey) {
      this.notifications.error('No environment selected');
      return;
    }
    const problem = this.validate();
    if (problem) {
      this.notifications.error(problem);
      return;
    }

    const shared = {
      name: this.form.name.trim(),
      description: this.form.description.trim() || null,
      enabled: this.form.enabled,
      rolloutPercentage: this.form.type === 'MULTIVARIATE' ? 100 : this.form.rolloutPercentage,
      variants: this.form.type === 'MULTIVARIATE' ? this.form.variants : [],
      defaultVariantKey: this.form.type === 'MULTIVARIATE' ? this.form.defaultVariantKey : null,
      targetingRules: this.form.targetingRules,
      segmentKeys: this.form.segmentKeys,
    };

    this.saving = true;
    const request =
      this.mode === 'create'
        ? this.api.createFlag(envKey, { key: this.flagKey.trim(), type: this.form.type, ...shared })
        : this.api.updateFlag(envKey, this.flagKey, shared);

    request.subscribe({
      next: (flag) => {
        this.saving = false;
        this.notifications.success(`${this.mode === 'create' ? 'Created' : 'Updated'} flag '${flag.key}'`);
        this.router.navigate(['/flags']);
      },
      error: (err) => {
        this.saving = false;
        this.notifications.error(apiErrorMessage(err, 'Failed to save flag'));
      },
    });
  }
}
