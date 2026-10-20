import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { FlagFormComponent } from './flag-form.component';

const BASE = `${environment.apiBaseUrl}/api/v1`;

function createComponent(flagKeyParam: string | null) {
  localStorage.clear();
  TestBed.configureTestingModule({
    imports: [FlagFormComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(flagKeyParam ? { flagKey: flagKeyParam } : {}) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(FlagFormComponent);
  const context = TestBed.inject(EnvironmentContextService);
  context.setEnvironments([{ key: 'prod', name: 'Production', apiKey: 'k', createdAt: new Date().toISOString() }]);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, component: fixture.componentInstance, httpMock };
}

describe('FlagFormComponent', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('starts in create mode and loads the segment list for the current environment', () => {
    const { fixture, component, httpMock } = createComponent(null);
    fixture.detectChanges();

    expect(component.mode).toBe('create');
    httpMock.expectOne(`${BASE}/environments/prod/segments`).flush([]);
  });

  it('loads the existing flag in edit mode and seeds the form', () => {
    const { fixture, component, httpMock } = createComponent('new-checkout');
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/environments/prod/segments`).flush([]);
    httpMock.expectOne(`${BASE}/environments/prod/flags/new-checkout`).flush({
      id: '1',
      key: 'new-checkout',
      environmentKey: 'prod',
      name: 'New checkout',
      description: null,
      type: 'BOOLEAN',
      enabled: true,
      rolloutPercentage: 25,
      variants: [],
      defaultVariantKey: null,
      targetingRules: [],
      segmentKeys: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });

    expect(component.mode).toBe('edit');
    expect(component.form.name).toBe('New checkout');
    expect(component.form.rolloutPercentage).toBe(25);
  });

  it('rejects an invalid key before calling the API', () => {
    const { fixture, component, httpMock } = createComponent(null);
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/environments/prod/segments`).flush([]);

    component.flagKey = 'Not Valid Key!';
    component.form.name = 'Something';
    component.save();

    httpMock.expectNone(`${BASE}/environments/prod/flags`);
    expect(component.saving).toBe(false);
  });

  it('rejects multivariate flags whose variant weights do not sum to 100', () => {
    const { fixture, component, httpMock } = createComponent(null);
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/environments/prod/segments`).flush([]);

    component.flagKey = 'new-flag';
    component.form.name = 'New flag';
    component.onTypeChange('MULTIVARIATE');
    component.updateVariant(0, { weight: 40 });

    component.save();

    expect(component.saving).toBe(false);
    httpMock.expectNone(`${BASE}/environments/prod/flags`);
  });

  it('submits a well-formed multivariate flag and reports success', () => {
    const { fixture, component, httpMock } = createComponent(null);
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/environments/prod/segments`).flush([]);

    component.flagKey = 'new-flag';
    component.form.name = 'New flag';
    component.onTypeChange('MULTIVARIATE');
    component.save();

    const req = httpMock.expectOne(`${BASE}/environments/prod/flags`);
    expect(req.request.body.type).toBe('MULTIVARIATE');
    expect(req.request.body.variants.length).toBe(2);
    req.flush({ key: 'new-flag', ...req.request.body, id: '1', environmentKey: 'prod', createdAt: 'x', updatedAt: 'x' });

    expect(component.saving).toBe(false);
  });
});
