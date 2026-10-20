import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiService } from './core/api.service';
import { EnvironmentContextService } from './core/environment-context.service';
import { NotificationService } from './core/notification.service';
import { apiErrorMessage } from './core/api-error';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  constructor(
    private readonly api: ApiService,
    readonly context: EnvironmentContextService,
    readonly notifications: NotificationService,
  ) {}

  ngOnInit(): void {
    this.reloadEnvironments();
  }

  reloadEnvironments(): void {
    this.api.listEnvironments().subscribe({
      next: (envs) => this.context.setEnvironments(envs),
      error: (err) => this.notifications.error(apiErrorMessage(err, 'Failed to load environments')),
    });
  }

  onEnvironmentChange(key: string): void {
    this.context.setCurrentEnvironmentKey(key);
  }

  onActorChange(value: string): void {
    this.context.setActor(value);
  }
}
