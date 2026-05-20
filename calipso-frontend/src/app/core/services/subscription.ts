import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import {
  CompanySubscriptionSummary,
  SubscriptionPlan,
  SubscriptionPlanCode,
  WalletTransaction,
} from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class SubscriptionService {
  private readonly apiUrl = `${API_BASE}/subscription`;

  constructor(private http: HttpClient) {}

  findPlans(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${this.apiUrl}/plans`);
  }

  findCompanySubscription(companyId: number): Observable<CompanySubscriptionSummary> {
    return this.http.get<CompanySubscriptionSummary>(`${this.apiUrl}/companies/${companyId}`);
  }

  subscribe(companyId: number, planCode: SubscriptionPlanCode): Observable<CompanySubscriptionSummary> {
    return this.http.post<CompanySubscriptionSummary>(`${this.apiUrl}/companies/${companyId}/subscribe`, { planCode });
  }

  rechargeWallet(companyId: number, smsUnits: number): Observable<CompanySubscriptionSummary> {
    return this.http.post<CompanySubscriptionSummary>(`${this.apiUrl}/companies/${companyId}/wallet/recharge`, { smsUnits });
  }

  findTransactions(companyId: number): Observable<WalletTransaction[]> {
    return this.http.get<WalletTransaction[]>(`${this.apiUrl}/companies/${companyId}/transactions`);
  }
}
