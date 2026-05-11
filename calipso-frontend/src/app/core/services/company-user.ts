import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { CompanyUser } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class CompanyUserService {
  private readonly apiUrl = `${API_BASE}/company-users`;

  constructor(private http: HttpClient) {}

  findByCompany(companyId: number): Observable<CompanyUser[]> {
    return this.http.get<CompanyUser[]>(`${this.apiUrl}/company/${companyId}`);
  }

  create(user: CompanyUser): Observable<CompanyUser> {
    return this.http.post<CompanyUser>(this.apiUrl, user);
  }

  updateStatus(userId: number, active: boolean): Observable<CompanyUser> {
    return this.http.patch<CompanyUser>(`${this.apiUrl}/${userId}/active?active=${active}`, {});
  }
}
