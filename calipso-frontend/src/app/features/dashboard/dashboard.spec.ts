import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { WorkspaceService } from '../../core/services/workspace';
import { API_BASE } from '../../core/api';
import { Dashboard } from './dashboard';

describe('Dashboard statistics periods', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [Dashboard], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
    { provide: AuthService, useValue: { currentUser: signal({ fullName: 'Zrango' }) } },
    { provide: WorkspaceService, useValue: { selectedCompanyId: signal(1), activeCompany: signal({ id: 1, smsBalance: 123 }) } }] }));

  it('applies the period to all campaign counters and preserves the current balance', () => {
    const fixture = TestBed.createComponent(Dashboard); fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_BASE}/campaigns/company/1`).flush([{ id: 1, name: 'Ancienne', status: 'COMPLETED', totalSent: 10, channel: 'SMS' }]);
    const component = fixture.componentInstance;
    expect(component.totalSentMessages()).toBe(10);
    component.period.set({ startDate: '2026-10-01', endDate: '2026-10-28' }); fixture.detectChanges();
    const request = http.expectOne(req => req.url === `${API_BASE}/campaigns/company/1` && req.params.get('endDate') === '2026-10-28');
    expect(request.request.params.get('startDate')).toBe('2026-10-01');
    request.flush([{ id: 2, name: 'Récente', status: 'DRAFT', totalSent: 0, channel: 'EMAIL' }]);
    expect(component.completedCampaigns()).toBe(0); expect(component.readyCampaigns()).toBe(1);
    expect(component.countByChannel('EMAIL')).toBe(1); expect(component.totalSentMessages()).toBe(0);
    expect(component.activeBalance).toBe(123); http.verify();
  });

  it('cancels a previous request when the period changes', () => {
    const fixture = TestBed.createComponent(Dashboard); fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    const previous = http.expectOne(`${API_BASE}/campaigns/company/1`);
    fixture.componentInstance.period.set({ startDate: '2026-10-28', endDate: '2026-10-28' }); fixture.detectChanges();
    expect(previous.cancelled).toBe(true);
    http.expectOne(req => req.params.get('startDate') === '2026-10-28').flush([]);
    expect(fixture.componentInstance.campaigns()).toEqual([]); http.verify();
  });
});
