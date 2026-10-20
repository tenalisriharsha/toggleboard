import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { SegmentFormComponent } from './segment-form.component';

const BASE = `${environment.apiBaseUrl}/api/v1`;

function createComponent(segmentKeyParam: string | null) {
  localStorage.clear();
  TestBed.configureTestingModule({
    imports: [SegmentFormComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(segmentKeyParam ? { segmentKey: segmentKeyParam } : {}) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(SegmentFormComponent);
  const context = TestBed.inject(EnvironmentContextService);
  context.setEnvironments([{ key: 'prod', name: 'Production', apiKey: 'k', createdAt: new Date().toISOString() }]);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, component: fixture.componentInstance, httpMock };
}

describe('SegmentFormComponent', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('rejects saving without at least one rule', () => {
    const { fixture, component, httpMock } = createComponent(null);
    fixture.detectChanges();

    component.segmentKey = 'beta-testers';
    component.name = 'Beta testers';
    component.save();

    expect(component.saving).toBe(false);
    httpMock.expectNone(`${BASE}/environments/prod/segments`);
  });

  it('creates a segment with its rules', () => {
    const { fixture, component, httpMock } = createComponent(null);
    fixture.detectChanges();

    component.segmentKey = 'beta-testers';
    component.name = 'Beta testers';
    component.onRulesChange([{ attribute: 'cohort', operator: 'EQUALS', values: ['beta'], overrideVariantKey: null }]);
    component.save();

    const req = httpMock.expectOne(`${BASE}/environments/prod/segments`);
    expect(req.request.body.key).toBe('beta-testers');
    expect(req.request.body.rules.length).toBe(1);
    req.flush({ id: '1', environmentKey: 'prod', key: 'beta-testers', name: 'Beta testers', description: null, rules: [], createdAt: 'x', updatedAt: 'x' });

    expect(component.saving).toBe(false);
  });

  it('loads an existing segment in edit mode', () => {
    const { fixture, component, httpMock } = createComponent('beta-testers');
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/environments/prod/segments/beta-testers`).flush({
      id: '1',
      environmentKey: 'prod',
      key: 'beta-testers',
      name: 'Beta testers',
      description: 'early access',
      rules: [{ attribute: 'cohort', operator: 'EQUALS', values: ['beta'], overrideVariantKey: null }],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });

    expect(component.mode).toBe('edit');
    expect(component.name).toBe('Beta testers');
    expect(component.rules.length).toBe(1);
  });
});
