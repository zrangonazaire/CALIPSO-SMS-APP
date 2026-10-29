import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { CompanyUser, CompanyUserRole } from '../models/api.models';

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

  updateRole(userId: number, role: CompanyUserRole): Observable<CompanyUser> {
    return this.http.patch<CompanyUser>(`${this.apiUrl}/${userId}/role?role=${role}`, {});
  }

  resetPassword(userId: number, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/${userId}/reset-password`, { newPassword });
  }

  delete(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${userId}`);
  }
}
