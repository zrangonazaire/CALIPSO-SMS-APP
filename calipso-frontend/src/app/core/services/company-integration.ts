import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { CompanyIntegrationSettings } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CompanyIntegrationService {
  constructor(private http: HttpClient) {}

  get(companyId: number): Observable<CompanyIntegrationSettings> {
    return this.http.get<CompanyIntegrationSettings>(`${API_BASE}/companies/${companyId}/integrations`);
  }

  save(companyId: number, settings: CompanyIntegrationSettings): Observable<CompanyIntegrationSettings> {
    return this.http.put<CompanyIntegrationSettings>(
      `${API_BASE}/companies/${companyId}/integrations`,
      settings,
    );
  }
}
