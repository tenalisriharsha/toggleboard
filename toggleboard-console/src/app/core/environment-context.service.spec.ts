import { TestBed } from '@angular/core/testing';
import { EnvironmentContextService } from './environment-context.service';
import { ToggleboardEnvironment } from './models';

function env(key: string): ToggleboardEnvironment {
  return { key, name: key, apiKey: `key-${key}`, createdAt: new Date().toISOString() };
}

describe('EnvironmentContextService', () => {
  let service: EnvironmentContextService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(EnvironmentContextService);
  });

  it('picks the first environment as current when none was selected before', () => {
    service.setEnvironments([env('prod'), env('staging')]);
    expect(service.currentEnvironmentKey()).toBe('prod');
  });

  it('keeps the current selection if it still exists in the new list', () => {
    service.setEnvironments([env('prod'), env('staging')]);
    service.setCurrentEnvironmentKey('staging');

    service.setEnvironments([env('prod'), env('staging'), env('dev')]);
    expect(service.currentEnvironmentKey()).toBe('staging');
  });

  it('falls back to the first environment if the current one was deleted', () => {
    service.setEnvironments([env('prod'), env('staging')]);
    service.setCurrentEnvironmentKey('staging');

    service.setEnvironments([env('prod')]);
    expect(service.currentEnvironmentKey()).toBe('prod');
  });

  it('defaults the actor and trims/falls back on blank input', () => {
    expect(service.actor()).toBe('console-admin');
    service.setActor('  alice  ');
    expect(service.actor()).toBe('alice');
    service.setActor('   ');
    expect(service.actor()).toBe('console-admin');
  });

  it('persists the current environment key to localStorage', () => {
    service.setCurrentEnvironmentKey('prod');
    expect(localStorage.getItem('toggleboard.currentEnvironmentKey')).toBe('prod');
  });
});
