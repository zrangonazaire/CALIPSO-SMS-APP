import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import {
  CompanySubscriptionSummary,
  PriceSimulationResponse,
  RechargeWalletPayload,
  SubscribePayload,
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

  simulatePricing(
    companyId: number,
    smsUnits: number,
    planCode?: SubscriptionPlanCode
  ): Observable<PriceSimulationResponse> {
    let params = new HttpParams().set('smsUnits', smsUnits);
    if (planCode) {
      params = params.set('planCode', planCode);
    }
    return this.http.get<PriceSimulationResponse>(`${this.apiUrl}/companies/${companyId}/simulate`, { params });
  }

  subscribe(
    companyId: number,
    payloadOrCode: SubscribePayload | SubscriptionPlanCode
  ): Observable<CompanySubscriptionSummary> {
    const body: SubscribePayload =
      typeof payloadOrCode === 'string' ? { planCode: payloadOrCode } : payloadOrCode;
    return this.http.post<CompanySubscriptionSummary>(`${this.apiUrl}/companies/${companyId}/subscribe`, body);
  }

  rechargeWallet(
    companyId: number,
    payloadOrUnits: RechargeWalletPayload | number
  ): Observable<CompanySubscriptionSummary> {
    const body: RechargeWalletPayload =
      typeof payloadOrUnits === 'number' ? { smsUnits: payloadOrUnits } : payloadOrUnits;
    return this.http.post<CompanySubscriptionSummary>(`${this.apiUrl}/companies/${companyId}/wallet/recharge`, body);
  }

  findTransactions(companyId: number): Observable<WalletTransaction[]> {
    return this.http.get<WalletTransaction[]>(`${this.apiUrl}/companies/${companyId}/transactions`);
  }
}
