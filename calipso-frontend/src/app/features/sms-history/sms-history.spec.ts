import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WorkspaceService } from '../../core/services/workspace';
import { API_BASE } from '../../core/api';
import { SmsHistory } from './sms-history';

describe('History statistics periods', () => {
  it('combines the requested period with the existing channel and source filters', () => {
    TestBed.configureTestingModule({ imports: [SmsHistory], providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: WorkspaceService, useValue: { selectedCompanyId: signal(1) } }] });
    const fixture = TestBed.createComponent(SmsHistory); fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_BASE}/sms-history/company/1`).flush([]);
    const component = fixture.componentInstance;
    component.channelFilter.set('SMS'); component.sourceFilter.set('MANUAL');
    component.period.set({ startDate: '2026-10-28', endDate: '2026-10-28' }); fixture.detectChanges();
    const request = http.expectOne(req => req.url === `${API_BASE}/sms-history/company/1` && req.params.get('startDate') === '2026-10-28');
    request.flush([
      { id: 1, channel: 'SMS', source: 'MANUAL', status: 'SENT', phoneNumber: '0701234567', message: 'Bonjour', segmentCount: 2 },
      { id: 2, channel: 'EMAIL', source: 'MANUAL', status: 'SENT', phoneNumber: 'awa@example.ci', message: 'Bonjour', segmentCount: 1 },
    ]);
    expect(component.filteredHistory()).toHaveLength(1); expect(component.totalSegments).toBe(2);
    expect(component.sourceFilter()).toBe('MANUAL'); http.verify();
  });
});
