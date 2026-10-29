import { DatePeriod, periodParams } from '../models/date-period';
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { SmsSendHistory } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class SmsHistoryService {
  private readonly apiUrl = `${API_BASE}/sms-history`;

  constructor(private http: HttpClient) {}

  findByCompany(companyId: number, period: DatePeriod = {}): Observable<SmsSendHistory[]> {
    return this.http.get<SmsSendHistory[]>(`${this.apiUrl}/company/${companyId}`, { params: periodParams(period) });
  }
}
