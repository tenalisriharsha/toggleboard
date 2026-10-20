import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { NotificationService } from '../../core/notification.service';
import { apiErrorMessage } from '../../core/api-error';
import { CreateSegmentRequest, Segment, TargetingRule } from '../../core/models';
import { TargetingRulesEditorComponent } from '../../shared/targeting-rules-editor/targeting-rules-editor.component';

const KEY_PATTERN = /^[a-z0-9][a-z0-9-_]*$/;

@Component({
  selector: 'app-segment-form',
  standalone: true,
  imports: [RouterLink, TargetingRulesEditorComponent],
  templateUrl: './segment-form.component.html',
  styleUrl: './segment-form.component.css',
})
export class SegmentFormComponent implements OnInit {
  mode: 'create' | 'edit' = 'create';
  segmentKey = '';
  loading = false;
  saving = false;

  name = '';
  description = '';
  rules: TargetingRule[] = [];

  constructor(
    private readonly api: ApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    readonly context: EnvironmentContextService,
    private readonly notifications: NotificationService,
  ) {}

  ngOnInit(): void {
    const paramKey = this.route.snapshot.paramMap.get('segmentKey');
    this.mode = paramKey ? 'edit' : 'create';
    this.segmentKey = paramKey ?? '';

    const envKey = this.context.currentEnvironmentKey();
    if (!envKey || this.mode !== 'edit' || !paramKey) {
      return;
    }

    this.loading = true;
    this.api.getSegment(envKey, paramKey).subscribe({
      next: (segment: Segment) => {
        this.loading = false;
        this.name = segment.name;
        this.description = segment.description ?? '';
        this.rules = segment.rules.map((r) => ({ ...r, values: [...r.values] }));
      },
      error: (err) => {
        this.loading = false;
        this.notifications.error(apiErrorMessage(err, 'Failed to load segment'));
      },
    });
  }

  onRulesChange(rules: TargetingRule[]): void {
    this.rules = rules;
  }

  private validate(): string | null {
    if (this.mode === 'create' && !KEY_PATTERN.test(this.segmentKey)) {
      return 'Key must be lowercase, alphanumeric, "-" or "_", starting with a letter or digit';
    }
    if (!this.name.trim()) {
      return 'Name is required';
    }
    if (this.rules.length === 0) {
      return 'A segment needs at least one targeting rule';
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

    const payload: Omit<CreateSegmentRequest, 'key'> = {
      name: this.name.trim(),
      description: this.description.trim() || null,
      rules: this.rules,
    };

    this.saving = true;
    const request =
      this.mode === 'create'
        ? this.api.createSegment(envKey, { key: this.segmentKey.trim(), ...payload })
        : this.api.updateSegment(envKey, this.segmentKey, payload);

    request.subscribe({
      next: (segment) => {
        this.saving = false;
        this.notifications.success(`${this.mode === 'create' ? 'Created' : 'Updated'} segment '${segment.key}'`);
        this.router.navigate(['/segments']);
      },
      error: (err) => {
        this.saving = false;
        this.notifications.error(apiErrorMessage(err, 'Failed to save segment'));
      },
    });
  }
}
