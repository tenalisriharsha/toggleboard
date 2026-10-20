import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { ApiService } from './api.service';

const BASE = `${environment.apiBaseUrl}/api/v1`;

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists environments', () => {
    service.listEnvironments().subscribe();
    const req = httpMock.expectOne(`${BASE}/environments`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('creates an environment with its key and name in the body', () => {
    service.createEnvironment('prod', 'Production').subscribe();
    const req = httpMock.expectOne(`${BASE}/environments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ key: 'prod', name: 'Production' });
    req.flush({ key: 'prod', name: 'Production', apiKey: 'k', createdAt: '2026-01-01T00:00:00Z' });
  });

  it('scopes flag endpoints under the environment key', () => {
    service.listFlags('prod').subscribe();
    httpMock.expectOne(`${BASE}/environments/prod/flags`).flush([]);

    service.getFlag('prod', 'new-checkout').subscribe();
    httpMock.expectOne(`${BASE}/environments/prod/flags/new-checkout`).flush({});

    service.deleteFlag('prod', 'new-checkout').subscribe();
    const del = httpMock.expectOne(`${BASE}/environments/prod/flags/new-checkout`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });

  it('builds the audit query params only when provided', () => {
    service.listAudit('prod').subscribe();
    const bare = httpMock.expectOne(`${BASE}/environments/prod/audit`);
    expect(bare.request.params.keys().length).toBe(0);
    bare.flush([]);

    service.listAudit('prod', 'FLAG', 10).subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === `${BASE}/environments/prod/audit` && r.params.get('entityType') === 'FLAG' && r.params.get('limit') === '10',
    );
    req.flush([]);
  });
});
