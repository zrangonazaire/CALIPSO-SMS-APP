import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WorkspaceService } from '../../core/services/workspace';
import { API_BASE } from '../../core/api';
import { SmsApi } from './sms-api';

describe('Connector statistics periods', () => {
  it('requests a period and refreshes counters while keeping the current wallet balance', () => {
    TestBed.configureTestingModule({ imports: [SmsApi], providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: WorkspaceService, useValue: { selectedCompanyId: signal(1) } }] });
    const fixture = TestBed.createComponent(SmsApi); fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    const response = { companyId: 1, companyName: 'MAGISER', remainingSmsBalance: 123, totalMessages: 10, acceptedMessages: 8, deliveredMessages: 6, failedMessages: 2, pendingMessages: 0, consumedSegments: 8, failedSegments: 2, successRate: 80, contracts: [], recentMessages: [] };
    http.expectOne(`${API_BASE}/sms-api/company/1/dashboard`).flush(response);
    fixture.componentInstance.period.set({ startDate: '2026-10-01', endDate: '2026-10-28' }); fixture.detectChanges();
    const request = http.expectOne(req => req.url === `${API_BASE}/sms-api/company/1/dashboard` && req.params.get('endDate') === '2026-10-28');
    expect(request.request.params.get('startDate')).toBe('2026-10-01');
    request.flush({ ...response, totalMessages: 2, acceptedMessages: 1, failedMessages: 1, successRate: 50 });
    expect(fixture.componentInstance.dashboard?.acceptedMessages).toBe(1);
    expect(fixture.componentInstance.dashboard?.remainingSmsBalance).toBe(123); http.verify();
  });
});
