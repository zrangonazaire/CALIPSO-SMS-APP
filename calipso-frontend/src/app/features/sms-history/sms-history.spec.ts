import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WorkspaceService } from '../../core/services/workspace';
import { API_BASE } from '../../core/api';
import { SmsHistory } from './sms-history';

describe('History statistics periods', () => {
  it('paginates 350 messages without truncating totals and resets after filtering', () => {
    TestBed.configureTestingModule({ imports: [SmsHistory], providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: WorkspaceService, useValue: { selectedCompanyId: signal(1) } }] });
    const fixture = TestBed.createComponent(SmsHistory); fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_BASE}/sms-history/company/1`).flush(Array.from({ length: 350 }, (_, i) => ({
      id: i + 1, channel: 'SMS', source: 'CAMPAIGN', status: i === 349 ? 'FAILED' : 'SENT',
      phoneNumber: `+22507${String(i + 1).padStart(8, '0')}`, message: `Message ${i + 1}`, segmentCount: 2,
    })));
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(fixture.nativeElement.querySelectorAll('.history-row')).toHaveLength(25);
    expect(component.pagedHistory().pageCount).toBe(14);
    component.page.set(14); fixture.detectChanges();
    expect(component.pagedHistory().items.map(item => item.id)).toEqual(Array.from({ length: 25 }, (_, i) => i + 326));
    expect(component.totalSegments).toBe(700);
    expect(component.campaignCount).toBe(350);
    component.statusFilter.set('FAILED'); fixture.detectChanges();
    expect(component.page()).toBe(1);
    expect(component.pagedHistory().items.map(item => item.id)).toEqual([350]);
    expect(component.totalSegments).toBe(2);
    component.statusFilter.set('ALL'); component.search.set('aucun résultat'); fixture.detectChanges();
    expect(component.pagedHistory().total).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('.history-row')).toHaveLength(0);
    http.verify(); fixture.destroy();
  });

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
