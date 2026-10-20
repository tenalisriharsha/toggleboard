import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { AuditLogComponent } from './audit-log.component';

const BASE = `${environment.apiBaseUrl}/api/v1`;

describe('AuditLogComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<AuditLogComponent>>;
  let component: AuditLogComponent;
  let httpMock: HttpTestingController;
  let context: EnvironmentContextService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(AuditLogComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    context = TestBed.inject(EnvironmentContextService);
  });

  afterEach(() => httpMock.verify());

  it('reloads with the entityType and limit filters when they change', () => {
    context.setEnvironments([{ key: 'prod', name: 'Production', apiKey: 'k', createdAt: '2026-01-01T00:00:00Z' }]);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === `${BASE}/environments/prod/audit`).flush([]);

    component.onEntityTypeChange('FLAG');
    const req = httpMock.expectOne(
      (r) => r.url === `${BASE}/environments/prod/audit` && r.params.get('entityType') === 'FLAG',
    );
    expect(req.request.params.get('limit')).toBe('50');
    req.flush([]);
  });

  it('pretty-prints JSON snapshots and falls back to the raw string on bad JSON', () => {
    expect(component.prettyJson('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(component.prettyJson('not json')).toBe('not json');
    expect(component.prettyJson(null)).toBe('');
  });

  it('toggles the expanded entry on repeated clicks', () => {
    component.toggleExpanded('entry-1');
    expect(component.expandedId).toBe('entry-1');
    component.toggleExpanded('entry-1');
    expect(component.expandedId).toBeNull();
  });
});
