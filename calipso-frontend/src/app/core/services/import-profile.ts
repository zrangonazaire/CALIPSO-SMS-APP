import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { ImportProfile } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class ImportProfileService {
  private readonly apiUrl = `${API_BASE}/import-profiles`;

  constructor(private http: HttpClient) {}

  findByCompany(companyId: number): Observable<ImportProfile[]> {
    return this.http.get<ImportProfile[]>(`${this.apiUrl}/company/${companyId}`);
  }

  create(profile: ImportProfile): Observable<ImportProfile> {
    return this.http.post<ImportProfile>(this.apiUrl, profile);
  }
}
