import { DatePeriod, periodParams } from '../models/date-period';
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { SmsApiDashboard } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class SmsApiService {
  private readonly apiUrl = `${API_BASE}/sms-api`;

  constructor(private http: HttpClient) {}

  findDashboard(companyId: number, period: DatePeriod = {}): Observable<SmsApiDashboard> {
    return this.http.get<SmsApiDashboard>(`${this.apiUrl}/company/${companyId}/dashboard`, { params: periodParams(period) });
  }
}
