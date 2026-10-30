import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { PlatformOverview } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class PlatformService {
  constructor(private http: HttpClient) {}

  getOverview(): Observable<PlatformOverview> {
    return this.http.get<PlatformOverview>(`${API_BASE}/platform/overview`);
  }
}
