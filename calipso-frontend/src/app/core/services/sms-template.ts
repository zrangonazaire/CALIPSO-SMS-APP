import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { SmsTemplate } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class SmsTemplateService {
  private readonly apiUrl = `${API_BASE}/sms-templates`;

  constructor(private http: HttpClient) {}

  findByProfile(profileId: number): Observable<SmsTemplate[]> {
    return this.http.get<SmsTemplate[]>(`${this.apiUrl}/profile/${profileId}`);
  }

  create(template: SmsTemplate): Observable<SmsTemplate> {
    return this.http.post<SmsTemplate>(this.apiUrl, template);
  }
}
