import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { EnvironmentContextService } from '../../core/environment-context.service';
import { Segment } from '../../core/models';
import { SegmentsListComponent } from './segments-list.component';

const BASE = `${environment.apiBaseUrl}/api/v1`;

function segment(): Segment {
  return {
    id: '1',
    environmentKey: 'prod',
    key: 'beta-testers',
    name: 'Beta testers',
    description: null,
    rules: [{ attribute: 'cohort', operator: 'EQUALS', values: ['beta'], overrideVariantKey: null }],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

describe('SegmentsListComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<SegmentsListComponent>>;
  let component: SegmentsListComponent;
  let httpMock: HttpTestingController;
  let context: EnvironmentContextService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [SegmentsListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(SegmentsListComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    context = TestBed.inject(EnvironmentContextService);
  });

  afterEach(() => httpMock.verify());

  it('loads segments once an environment becomes current', () => {
    fixture.detectChanges();
    context.setEnvironments([{ key: 'prod', name: 'Production', apiKey: 'k', createdAt: '2026-01-01T00:00:00Z' }]);
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/environments/prod/segments`).flush([segment()]);
    expect(component.segments.length).toBe(1);
  });

  it('removes a deleted segment from the list', () => {
    context.setEnvironments([{ key: 'prod', name: 'Production', apiKey: 'k', createdAt: '2026-01-01T00:00:00Z' }]);
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/environments/prod/segments`).flush([segment()]);

    spyOn(window, 'confirm').and.returnValue(true);
    component.deleteSegment(component.segments[0]);

    const req = httpMock.expectOne(`${BASE}/environments/prod/segments/beta-testers`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(component.segments.length).toBe(0);
  });
});
