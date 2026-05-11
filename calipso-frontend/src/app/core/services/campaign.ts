import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { Campaign } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class CampaignService {
  private readonly apiUrl = `${API_BASE}/campaigns`;

  constructor(private http: HttpClient) {}

  findByCompany(companyId: number): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(`${this.apiUrl}/company/${companyId}`);
  }

  create(campaign: Campaign): Observable<Campaign> {
    return this.http.post<Campaign>(this.apiUrl, campaign);
  }

  importExcel(campaignId: number, file: File): Observable<Campaign> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Campaign>(`${API_BASE}/excel/campaign/${campaignId}/import`, formData);
  }

  send(campaignId: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.apiUrl}/${campaignId}/send`, {});
  }
}
