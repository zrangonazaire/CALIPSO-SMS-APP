import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { ManualSmsRequest, ManualSmsResponse } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class ManualSmsService {
  private readonly apiUrl = `${API_BASE}/manual-sms`;

  constructor(private http: HttpClient) {}

  send(request: ManualSmsRequest): Observable<ManualSmsResponse> {
    return this.http.post<ManualSmsResponse>(`${this.apiUrl}/send`, request);
  }
}
