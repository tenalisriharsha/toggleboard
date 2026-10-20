import { Injectable, signal } from '@angular/core';
import { ToggleboardEnvironment } from './models';

const CURRENT_ENV_KEY = 'toggleboard.currentEnvironmentKey';
const ACTOR_KEY = 'toggleboard.actor';

/**
 * Holds which environment the console is currently pointed at, and the
 * "actor" name sent as X-Actor on every mutation for the audit log. Both
 * are just remembered per-browser (localStorage) — there's no real user
 * session yet, matching the API's own placeholder RBAC.
 */
@Injectable({ providedIn: 'root' })
export class EnvironmentContextService {
  readonly environments = signal<ToggleboardEnvironment[]>([]);
  readonly currentEnvironmentKey = signal<string | null>(this.readStored(CURRENT_ENV_KEY));
  readonly actor = signal<string>(this.readStored(ACTOR_KEY) ?? 'console-admin');

  setEnvironments(environments: ToggleboardEnvironment[]): void {
    this.environments.set(environments);
    const current = this.currentEnvironmentKey();
    if ((!current || !environments.some((e) => e.key === current)) && environments.length > 0) {
      this.setCurrentEnvironmentKey(environments[0].key);
    }
  }

  setCurrentEnvironmentKey(key: string | null): void {
    this.currentEnvironmentKey.set(key);
    this.writeStored(CURRENT_ENV_KEY, key);
  }

  setActor(actor: string): void {
    const trimmed = actor.trim() || 'console-admin';
    this.actor.set(trimmed);
    this.writeStored(ACTOR_KEY, trimmed);
  }

  private readStored(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private writeStored(key: string, value: string | null): void {
    try {
      if (value === null) {
        localStorage.removeItem(key);
      } else {
        localStorage.setItem(key, value);
      }
    } catch {
      /* ignore unavailable storage (private window, etc.) */
    }
  }
}
