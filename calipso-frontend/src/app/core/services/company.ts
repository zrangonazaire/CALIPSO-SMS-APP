import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { Company, CompanySubscriptionSummary } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class CompanyService {
  private readonly apiUrl = `${API_BASE}/companies`;

  constructor(private http: HttpClient) {}

  findAll(): Observable<Company[]> {
    return this.http.get<Company[]>(this.apiUrl);
  }

  create(company: Company): Observable<Company> {
    return this.http.post<Company>(this.apiUrl, company);
  }

  update(companyId: number, company: Company): Observable<Company> {
    return this.http.put<Company>(`${this.apiUrl}/${companyId}`, company);
  }

  rechargeWallet(companyId: number, smsUnits: number): Observable<CompanySubscriptionSummary> {
    return this.http.post<CompanySubscriptionSummary>(`${this.apiUrl}/${companyId}/wallet/recharge`, { smsUnits });
  }
}
