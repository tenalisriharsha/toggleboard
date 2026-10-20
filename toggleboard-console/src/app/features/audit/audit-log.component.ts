import { DatePipe } from '@angular/common';
import { Component, effect } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { NotificationService } from '../../core/notification.service';
import { apiErrorMessage } from '../../core/api-error';
import { AuditEntityType, AuditEntry } from '../../core/models';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.css',
})
export class AuditLogComponent {
  entries: AuditEntry[] = [];
  loading = false;
  entityType: AuditEntityType | '' = '';
  limit = 50;
  expandedId: string | null = null;

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
      this.entries = [];
      return;
    }
    this.loading = true;
    this.api.listAudit(envKey, this.entityType || null, this.limit).subscribe({
      next: (entries) => {
        this.loading = false;
        this.entries = entries;
      },
      error: (err) => {
        this.loading = false;
        this.notifications.error(apiErrorMessage(err, 'Failed to load audit log'));
      },
    });
  }

  onEntityTypeChange(value: string): void {
    this.entityType = value as AuditEntityType | '';
    this.reload();
  }

  onLimitChange(value: number): void {
    this.limit = value > 0 ? value : 50;
    this.reload();
  }

  toggleExpanded(id: string): void {
    this.expandedId = this.expandedId === id ? null : id;
  }

  prettyJson(value: string | null): string {
    if (!value) {
      return '';
    }
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
}
