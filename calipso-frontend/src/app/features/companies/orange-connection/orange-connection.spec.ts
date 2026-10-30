import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE } from '../../../core/api';
import { OrangeConnection } from './orange-connection';

describe('Orange connection form', () => {
  function setup() {
    TestBed.configureTestingModule({
      imports: [OrangeConnection],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(OrangeConnection);
    fixture.componentRef.setInput('companyId', 8);
    fixture.componentRef.setInput('companyName', 'MAGISER');
    fixture.componentRef.setInput('configured', true);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne(`${API_BASE}/companies/8/integrations/orange/configuration`)
      .flush({
        tokenUrl: 'https://api.orange.com/oauth/v3/token',
        messagingUrl: 'https://api.orange.com/smsmessaging/v1/outbound/tel%3A%2B2250000/requests',
        contractsUrl: 'https://api.orange.com/sms/admin/v1/contracts?country=CIV',
      });
    return { fixture, component: fixture.componentInstance, http };
  }

  it('explains automatic Bearer generation and tests stored credentials without saving', () => {
    const { fixture, component, http } = setup();
    component.test();
    const request = http.expectOne(`${API_BASE}/companies/8/integrations/orange/test`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.clientId).toBeUndefined();
    request.flush({
      authenticated: true,
      tokenType: 'Bearer',
      tokenExpiresAt: '2026-10-30T20:00:00Z',
      contractsAccessible: true,
      availableUnits: 445,
      contracts: [],
      message: 'Connexion vérifiée',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('445 SMS');
    expect(fixture.nativeElement.querySelector('#orange-bearer-8').readOnly).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('sans envoi de SMS');
    http.verify();
  });

  it('rejects incomplete credentials and cancels tests when the company changes', () => {
    const { fixture, component, http } = setup();
    fixture.componentRef.setInput('clientId', 'draft-id');
    fixture.detectChanges();
    component.test();
    expect(component.error()).toContain('ensemble');
    http.expectNone(`${API_BASE}/companies/8/integrations/orange/test`);
    fixture.componentRef.setInput('clientSecret', 'draft-secret');
    fixture.detectChanges();
    component.test();
    const request = http.expectOne(`${API_BASE}/companies/8/integrations/orange/test`);
    expect(request.request.body).toEqual({ clientId: 'draft-id', clientSecret: 'draft-secret' });
    fixture.componentRef.setInput('companyId', 9);
    fixture.detectChanges();
    expect(request.cancelled).toBe(true);
    expect(component.result()).toBeUndefined();
    http.expectOne(`${API_BASE}/companies/9/integrations/orange/configuration`).flush({});
    http.verify();
  });
});
