import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { Flag } from '../../core/models';
import { FlagsListComponent } from './flags-list.component';

const BASE = `${environment.apiBaseUrl}/api/v1`;

function flag(overrides: Partial<Flag> = {}): Flag {
  return {
    id: '1',
    key: 'checkout',
    environmentKey: 'prod',
    name: 'Checkout',
    description: null,
    type: 'BOOLEAN',
    enabled: false,
    rolloutPercentage: 10,
    variants: [],
    defaultVariantKey: null,
    targetingRules: [],
    segmentKeys: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('FlagsListComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<FlagsListComponent>>;
  let component: FlagsListComponent;
  let httpMock: HttpTestingController;
  let context: EnvironmentContextService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [FlagsListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(FlagsListComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    context = TestBed.inject(EnvironmentContextService);
  });

  afterEach(() => httpMock.verify());

  it('does nothing until an environment is selected', () => {
    fixture.detectChanges();
    httpMock.expectNone(() => true);
    expect(component.flags).toEqual([]);
  });

  it('loads flags once an environment becomes current', () => {
    fixture.detectChanges();
    context.setEnvironments([{ key: 'prod', name: 'Production', apiKey: 'k', createdAt: '2026-01-01T00:00:00Z' }]);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${BASE}/environments/prod/flags`);
    req.flush([flag()]);

    expect(component.flags.length).toBe(1);
  });

  it('flips the kill switch and keeps the rest of the flag unchanged', () => {
    context.setEnvironments([{ key: 'prod', name: 'Production', apiKey: 'k', createdAt: '2026-01-01T00:00:00Z' }]);
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/environments/prod/flags`).flush([flag({ enabled: false })]);

    component.toggleKillSwitch(component.flags[0]);

    const req = httpMock.expectOne(`${BASE}/environments/prod/flags/checkout`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.enabled).toBe(true);
    expect(req.request.body.rolloutPercentage).toBe(10);
    req.flush(flag({ enabled: true }));

    expect(component.flags[0].enabled).toBe(true);
    expect(component.togglingKey).toBeNull();
  });
});
