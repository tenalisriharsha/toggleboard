import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { EnvironmentsPageComponent } from './environments-page.component';

const BASE = `${environment.apiBaseUrl}/api/v1`;

describe('EnvironmentsPageComponent', () => {
  let component: EnvironmentsPageComponent;
  let httpMock: HttpTestingController;
  let context: EnvironmentContextService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [EnvironmentsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(EnvironmentsPageComponent).componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    context = TestBed.inject(EnvironmentContextService);
  });

  afterEach(() => httpMock.verify());

  it('rejects creation when the key or name is blank', () => {
    component.newKey = '';
    component.newName = 'Production';
    component.create();
    expect(component.creating).toBe(false);
    httpMock.expectNone(`${BASE}/environments`);
  });

  it('creates an environment, adds it to the context and makes it current', () => {
    component.newKey = 'staging';
    component.newName = 'Staging';
    component.create();

    const req = httpMock.expectOne(`${BASE}/environments`);
    req.flush({ key: 'staging', name: 'Staging', apiKey: 'abc123', createdAt: '2026-01-01T00:00:00Z' });

    expect(context.environments().some((e) => e.key === 'staging')).toBe(true);
    expect(context.currentEnvironmentKey()).toBe('staging');
    expect(component.newKey).toBe('');
    expect(component.creating).toBe(false);
  });
});
