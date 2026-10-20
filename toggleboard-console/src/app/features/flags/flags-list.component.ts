import { Component, effect } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { NotificationService } from '../../core/notification.service';
import { apiErrorMessage } from '../../core/api-error';
import { Flag } from '../../core/models';

@Component({
  selector: 'app-flags-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './flags-list.component.html',
  styleUrl: './flags-list.component.css',
})
export class FlagsListComponent {
  flags: Flag[] = [];
  loading = false;
  togglingKey: string | null = null;

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
      this.flags = [];
      return;
    }
    this.loading = true;
    this.api.listFlags(envKey).subscribe({
      next: (flags) => {
        this.loading = false;
        this.flags = flags;
      },
      error: (err) => {
        this.loading = false;
        this.notifications.error(apiErrorMessage(err, 'Failed to load flags'));
      },
    });
  }

  toggleKillSwitch(flag: Flag): void {
    const envKey = this.environmentKey;
    if (!envKey) {
      return;
    }
    this.togglingKey = flag.key;
    this.api
      .updateFlag(envKey, flag.key, {
        name: flag.name,
        description: flag.description,
        enabled: !flag.enabled,
        rolloutPercentage: flag.rolloutPercentage,
        variants: flag.variants,
        defaultVariantKey: flag.defaultVariantKey,
        targetingRules: flag.targetingRules,
        segmentKeys: flag.segmentKeys,
      })
      .subscribe({
        next: (updated) => {
          this.togglingKey = null;
          this.flags = this.flags.map((f) => (f.key === updated.key ? updated : f));
          this.notifications.success(`'${updated.key}' is now ${updated.enabled ? 'enabled' : 'disabled'}`);
        },
        error: (err) => {
          this.togglingKey = null;
          this.notifications.error(apiErrorMessage(err, 'Failed to toggle flag'));
        },
      });
  }

  deleteFlag(flag: Flag): void {
    const envKey = this.environmentKey;
    if (!envKey) {
      return;
    }
    if (!confirm(`Delete flag '${flag.key}'? This cannot be undone.`)) {
      return;
    }
    this.api.deleteFlag(envKey, flag.key).subscribe({
      next: () => {
        this.flags = this.flags.filter((f) => f.key !== flag.key);
        this.notifications.success(`Deleted flag '${flag.key}'`);
      },
      error: (err) => this.notifications.error(apiErrorMessage(err, 'Failed to delete flag')),
    });
  }
}
