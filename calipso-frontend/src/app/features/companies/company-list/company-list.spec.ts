import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth';
import { API_BASE } from '../../../core/api';
import { CompanyIntegrationSettings } from '../../../core/models/api.models';
import { WorkspaceService } from '../../../core/services/workspace';
import { CompanyUserService } from '../../../core/services/company-user';
import { ImportProfileService } from '../../../core/services/import-profile';
import { PlatformService } from '../../../core/services/platform';
import { CompanyList } from './company-list';

describe('Company integration persistence', () => {
  let fixture: ComponentFixture<CompanyList>;
  let component: CompanyList;
  let http: HttpTestingController;
  let selectedId: ReturnType<typeof signal<number>>;
  const url = `${API_BASE}/companies/8/integrations`;
  const saved: CompanyIntegrationSettings = {
    companyId: 8, smsSenderAddress: '+2250000', orangeSenderName: 'MAGISER',
    orangeCredentialsConfigured: true, whatsappCredentialsConfigured: false,
    smtpPasswordConfigured: false, smtpStartTls: true,
  };

  beforeEach(() => {
    selectedId = signal(8);
    TestBed.configureTestingModule({
      imports: [CompanyList],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({ tab: 'integrations' })) } },
        { provide: AuthService, useValue: { currentUser: signal(null) } },
        { provide: WorkspaceService, useValue: {
          selectedCompanyId: selectedId, isPlatformAdmin: () => true, canManageTeam: () => true,
          companies: () => [{ id: 8, name: 'MAGISER' }, { id: 9, name: 'Autre' }],
          activeCompany: () => ({ id: selectedId(), name: selectedId() === 8 ? 'MAGISER' : 'Autre' }),
        } },
        { provide: CompanyUserService, useValue: { findByCompany: () => of([]) } },
        { provide: ImportProfileService, useValue: { findByCompany: () => of([]) } },
        { provide: PlatformService, useValue: { getOverview: () => of(undefined) } },
      ],
    });
    fixture = TestBed.createComponent(CompanyList);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushOrangeUrls();
  });

  afterEach(() => { http.verify(); fixture.destroy(); });

  function flushOrangeUrls(): void {
    http.match(req => req.url.endsWith('/orange/configuration')).forEach(req => req.flush({
      tokenUrl: 'https://api.orange.com/oauth/v3/token', messagingUrl: '', contractsUrl: '',
    }));
  }

  it('blocks saving until the existing configuration has loaded', () => {
    component.saveIntegrations();
    http.expectNone(req => req.method === 'PUT');
    expect(component.integrationReady()).toBe(false);
    http.expectOne(url).flush(saved);
    expect(component.integrationReady()).toBe(true);
  });

  it('tests saved SMTP without transmitting draft secrets or sending email', () => {
    http.expectOne(url).flush({ ...saved, smtpHost: 'mail.example.test', smtpPort: 465 });
    component.integrationSettings.smtpPassword = 'unsaved-password';
    component.testSavedSmtp(); component.testSavedSmtp();
    const test = http.expectOne(`${url}/smtp/test`);
    expect(test.request.method).toBe('POST');
    expect(test.request.body).toEqual({});
    test.flush({ connected: true, security: 'SSL/TLS', message: 'Connexion vérifiée. Aucun e-mail envoyé.' });
    expect(component.smtpResult()?.connected).toBe(true);
    expect(component.smtpTesting()).toBe(false);
  });

  it('ignores old SMTP results after changing the company', () => {
    http.expectOne(url).flush(saved);
    component.testSavedSmtp();
    const previous = http.expectOne(`${url}/smtp/test`);
    selectedId.set(9); fixture.detectChanges(); flushOrangeUrls();
    http.expectOne(`${API_BASE}/companies/9/integrations`).flush({ ...saved, companyId: 9 });
    previous.flush({ connected: true, security: 'SSL/TLS', message: 'Ancienne entreprise' });
    expect(component.smtpResult()).toBeUndefined();
    expect(component.smtpTesting()).toBe(false);
  });

  it('saves to the selected company, preserves blank secrets, then confirms a fresh server read', () => {
    http.expectOne(url).flush(saved);
    component.integrationSettings.orangeClientId = '';
    component.integrationSettings.orangeClientSecret = '   ';
    component.integrationSettings.whatsappAccessToken = '';
    component.integrationSettings.smtpPassword = '';
    component.saveIntegrations();
    component.saveIntegrations();
    const request = http.expectOne(req => req.url === url && req.method === 'PUT');
    expect(request.request.body.orangeSenderName).toBe('MAGISER');
    for (const key of ['orangeClientId', 'orangeClientSecret', 'whatsappAccessToken', 'smtpPassword']) {
      expect(request.request.body[key]).toBeUndefined();
    }
    request.flush(saved);
    expect(component.success).toBe('');
    expect(component.integrationSaving()).toBe(true);
    http.expectOne(url).flush(saved);
    expect(component.success).toContain('enregistrée et relue pour MAGISER');
    expect(component.integrationSettings.orangeCredentialsConfigured).toBe(true);
    expect(component.integrationSettings.orangeClientSecret).toBeUndefined();
    expect(component.integrationSaving()).toBe(false);
  });

  it('clears previous success when saving is rejected', () => {
    http.expectOne(url).flush(saved);
    component.success = 'Ancien succès';
    component.saveIntegrations();
    http.expectOne(url).flush({ message: 'Nom invalide' }, { status: 400, statusText: 'Bad Request' });
    expect(component.success).toBe('');
    expect(component.error).toBe('Nom invalide');
    expect(component.integrationSaving()).toBe(false);
  });

  it('distinguishes a successful save from a failed verification read', () => {
    http.expectOne(url).flush(saved);
    component.saveIntegrations();
    http.expectOne(url).flush(saved);
    http.expectOne(url).flush({}, { status: 503, statusText: 'Unavailable' });
    expect(component.success).toBe('');
    expect(component.error).toContain('Enregistrement effectué, mais la relecture a échoué');
    expect(component.integrationSaving()).toBe(false);
  });

  it('ignores a previous company save response after changing company', () => {
    http.expectOne(url).flush(saved);
    component.saveIntegrations();
    const write = http.expectOne(url);
    selectedId.set(9);
    fixture.detectChanges();
    flushOrangeUrls();
    const otherUrl = `${API_BASE}/companies/9/integrations`;
    http.expectOne(otherUrl).flush({ ...saved, companyId: 9, orangeSenderName: 'AUTRE' });
    write.flush(saved);
    http.expectOne(url).flush(saved);
    expect(component.integrationSettings.companyId).toBe(9);
    expect(component.integrationSettings.orangeSenderName).toBe('AUTRE');
    expect(component.success).toBe('');
  });
});
