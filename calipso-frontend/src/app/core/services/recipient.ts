import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { Recipient } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class RecipientService {
  private readonly apiUrl = `${API_BASE}/recipients`;

  constructor(private http: HttpClient) {}

  findByCampaign(campaignId: number): Observable<Recipient[]> {
    return this.http.get<Recipient[]>(`${this.apiUrl}/campaign/${campaignId}`);
  }
}
