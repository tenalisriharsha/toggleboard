import { Component, effect } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { NotificationService } from '../../core/notification.service';
import { apiErrorMessage } from '../../core/api-error';
import { Segment } from '../../core/models';

@Component({
  selector: 'app-segments-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './segments-list.component.html',
  styleUrl: './segments-list.component.css',
})
export class SegmentsListComponent {
  segments: Segment[] = [];
  loading = false;

  constructor(
    private readonly api: ApiService,
    readonly context: EnvironmentContextService,
    private readonly notifications: NotificationService,
  ) {
    effect(() => {
      this.context.currentEnvironmentKey();
      this.reload();
    });
  }

  get environmentKey(): string | null {
    return this.context.currentEnvironmentKey();
  }

  reload(): void {
    const envKey = this.environmentKey;
    if (!envKey) {
      this.segments = [];
      return;
    }
    this.loading = true;
    this.api.listSegments(envKey).subscribe({
      next: (segments) => {
        this.loading = false;
        this.segments = segments;
      },
      error: (err) => {
        this.loading = false;
        this.notifications.error(apiErrorMessage(err, 'Failed to load segments'));
      },
    });
  }

  deleteSegment(segment: Segment): void {
    const envKey = this.environmentKey;
    if (!envKey) {
      return;
    }
    if (!confirm(`Delete segment '${segment.key}'? Any flag still referencing it will fail its next update.`)) {
      return;
    }
    this.api.deleteSegment(envKey, segment.key).subscribe({
      next: () => {
        this.segments = this.segments.filter((s) => s.key !== segment.key);
        this.notifications.success(`Deleted segment '${segment.key}'`);
      },
      error: (err) => this.notifications.error(apiErrorMessage(err, 'Failed to delete segment')),
    });
  }
}
