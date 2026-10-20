import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AuditEntityType,
  AuditEntry,
  CreateFlagRequest,
  CreateSegmentRequest,
  Flag,
  Segment,
  ToggleboardEnvironment,
  UpdateFlagRequest,
  UpdateSegmentRequest,
} from './models';

const BASE = `${environment.apiBaseUrl}/api/v1`;

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  listEnvironments(): Observable<ToggleboardEnvironment[]> {
    return this.http.get<ToggleboardEnvironment[]>(`${BASE}/environments`);
  }

  createEnvironment(key: string, name: string): Observable<ToggleboardEnvironment> {
    return this.http.post<ToggleboardEnvironment>(`${BASE}/environments`, { key, name });
  }

  listFlags(environmentKey: string): Observable<Flag[]> {
    return this.http.get<Flag[]>(`${BASE}/environments/${environmentKey}/flags`);
  }

  getFlag(environmentKey: string, flagKey: string): Observable<Flag> {
    return this.http.get<Flag>(`${BASE}/environments/${environmentKey}/flags/${flagKey}`);
  }

  createFlag(environmentKey: string, request: CreateFlagRequest): Observable<Flag> {
    return this.http.post<Flag>(`${BASE}/environments/${environmentKey}/flags`, request);
  }

  updateFlag(environmentKey: string, flagKey: string, request: UpdateFlagRequest): Observable<Flag> {
    return this.http.put<Flag>(`${BASE}/environments/${environmentKey}/flags/${flagKey}`, request);
  }

  deleteFlag(environmentKey: string, flagKey: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/environments/${environmentKey}/flags/${flagKey}`);
  }

  listSegments(environmentKey: string): Observable<Segment[]> {
    return this.http.get<Segment[]>(`${BASE}/environments/${environmentKey}/segments`);
  }

  getSegment(environmentKey: string, segmentKey: string): Observable<Segment> {
    return this.http.get<Segment>(`${BASE}/environments/${environmentKey}/segments/${segmentKey}`);
  }

  createSegment(environmentKey: string, request: CreateSegmentRequest): Observable<Segment> {
    return this.http.post<Segment>(`${BASE}/environments/${environmentKey}/segments`, request);
  }

  updateSegment(environmentKey: string, segmentKey: string, request: UpdateSegmentRequest): Observable<Segment> {
    return this.http.put<Segment>(`${BASE}/environments/${environmentKey}/segments/${segmentKey}`, request);
  }

  deleteSegment(environmentKey: string, segmentKey: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/environments/${environmentKey}/segments/${segmentKey}`);
  }

  listAudit(environmentKey: string, entityType?: AuditEntityType | null, limit?: number | null): Observable<AuditEntry[]> {
    let params = new HttpParams();
    if (entityType) {
      params = params.set('entityType', entityType);
    }
    if (limit) {
      params = params.set('limit', limit);
    }
    return this.http.get<AuditEntry[]>(`${BASE}/environments/${environmentKey}/audit`, { params });
  }
}
