import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { HeaderModelRequest, ImportProfile } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ImportProfileService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${API_BASE}/import-profiles`;

  findByCompany(companyId: number): Observable<ImportProfile[]> {
    return this.http.get<ImportProfile[]>(`${this.apiUrl}/company/${companyId}`);
  }

  create(profile: ImportProfile): Observable<ImportProfile> {
    return this.http.post<ImportProfile>(this.apiUrl, profile);
  }

  saveModel(request: HeaderModelRequest, id?: number): Observable<ImportProfile> {
    return id
      ? this.http.put<ImportProfile>(`${this.apiUrl}/${id}/header-model`, request)
      : this.http.post<ImportProfile>(`${this.apiUrl}/header-models`, request);
  }

  duplicate(id: number): Observable<ImportProfile> {
    return this.http.post<ImportProfile>(`${this.apiUrl}/${id}/duplicate`, {});
  }

  archive(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  download(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/sample-excel`, { responseType: 'blob' });
  }
}
