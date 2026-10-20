import { DatePipe } from '@angular/common';
import { Component } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { NotificationService } from '../../core/notification.service';
import { apiErrorMessage } from '../../core/api-error';

@Component({
  selector: 'app-environments-page',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './environments-page.component.html',
  styleUrl: './environments-page.component.css',
})
export class EnvironmentsPageComponent {
  newKey = '';
  newName = '';
  creating = false;
  copiedKey: string | null = null;

  constructor(
    private readonly api: ApiService,
    readonly context: EnvironmentContextService,
    private readonly notifications: NotificationService,
  ) {}

  create(): void {
    const key = this.newKey.trim();
    const name = this.newName.trim();
    if (!key || !name) {
      this.notifications.error('Both a key and a name are required');
      return;
    }

    this.creating = true;
    this.api.createEnvironment(key, name).subscribe({
      next: (env) => {
        this.creating = false;
        this.newKey = '';
        this.newName = '';
        this.context.setEnvironments([...this.context.environments(), env]);
        this.context.setCurrentEnvironmentKey(env.key);
        this.notifications.success(`Created environment '${env.key}'`);
      },
      error: (err) => {
        this.creating = false;
        this.notifications.error(apiErrorMessage(err, 'Failed to create environment'));
      },
    });
  }

  selectAsCurrent(key: string): void {
    this.context.setCurrentEnvironmentKey(key);
    this.notifications.success(`Switched to environment '${key}'`);
  }

  copyApiKey(apiKey: string): void {
    navigator.clipboard
      ?.writeText(apiKey)
      .then(() => {
        this.copiedKey = apiKey;
        setTimeout(() => (this.copiedKey = null), 2000);
      })
      .catch(() => this.notifications.error('Could not copy to clipboard'));
  }
}
