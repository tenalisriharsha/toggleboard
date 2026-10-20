import { Injectable, signal } from '@angular/core';

export interface Notice {
  id: number;
  kind: 'success' | 'error';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  readonly notices = signal<Notice[]>([]);

  success(message: string): void {
    this.push('success', message);
  }

  error(message: string): void {
    this.push('error', message);
  }

  dismiss(id: number): void {
    this.notices.update((all) => all.filter((n) => n.id !== id));
  }

  private push(kind: Notice['kind'], message: string): void {
    const id = this.nextId++;
    this.notices.update((all) => [...all, { id, kind, message }]);
    setTimeout(() => this.dismiss(id), 6000);
  }
}
