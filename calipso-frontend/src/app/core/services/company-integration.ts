import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import {
  CompanyIntegrationSettings,
  OrangeConnectionConfiguration,
  OrangeConnectionStatus,
  SmtpConnectionStatus,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CompanyIntegrationService {
  private readonly http = inject(HttpClient);

  testSmtp(companyId: number): Observable<SmtpConnectionStatus> {
    return this.http.post<SmtpConnectionStatus>(`${API_BASE}/companies/${companyId}/integrations/smtp/test`, {});
  }

  orangeConfiguration(companyId: number): Observable<OrangeConnectionConfiguration> {
    return this.http.get<OrangeConnectionConfiguration>(
      `${API_BASE}/companies/${companyId}/integrations/orange/configuration`,
    );
  }

  testOrange(
    companyId: number,
    clientId?: string,
    clientSecret?: string,
  ): Observable<OrangeConnectionStatus> {
    return this.http.post<OrangeConnectionStatus>(
      `${API_BASE}/companies/${companyId}/integrations/orange/test`,
      { clientId, clientSecret },
    );
  }

  get(companyId: number): Observable<CompanyIntegrationSettings> {
    return this.http.get<CompanyIntegrationSettings>(
      `${API_BASE}/companies/${companyId}/integrations`,
    );
  }

  save(
    companyId: number,
    settings: CompanyIntegrationSettings,
  ): Observable<CompanyIntegrationSettings> {
    return this.http.put<CompanyIntegrationSettings>(
      `${API_BASE}/companies/${companyId}/integrations`,
      {
        ...settings,
        orangeClientId: settings.orangeClientId?.trim() ? settings.orangeClientId : undefined,
        orangeClientSecret: settings.orangeClientSecret?.trim() ? settings.orangeClientSecret : undefined,
        whatsappAccessToken: settings.whatsappAccessToken?.trim() ? settings.whatsappAccessToken : undefined,
        smtpPassword: settings.smtpPassword?.trim() ? settings.smtpPassword : undefined,
      },
    );
  }
}
