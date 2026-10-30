import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { API_BASE } from '../../../core/api';
import { Campaign, Recipient } from '../../../core/models/api.models';
import { WorkspaceService } from '../../../core/services/workspace';
import { CampaignList } from './campaign-list';

describe('Campaign and recipient pagination', () => {
  let fixture: ComponentFixture<CampaignList>;
  let component: CampaignList;
  let http: HttpTestingController;
  const companyId = signal(8);
  const campaigns: Campaign[] = Array.from({ length: 35 }, (_, i) => ({
    id: i + 1, name: `Campagne ${String(i + 1).padStart(3, '0')}`, channel: 'SMS',
    status: i % 2 ? 'DRAFT' : 'IMPORTED', totalRecipients: i === 0 ? 350 : 0,
    totalValid: i === 0 ? 349 : 0, totalSegments: i === 0 ? 700 : 0,
  }));
  const recipients: Recipient[] = Array.from({ length: 350 }, (_, i) => ({
    id: i + 1, recipientName: `Élodie ${i + 1}`, phoneNumber: `+22507${String(i + 1).padStart(8, '0')}`,
    generatedMessage: `Bonjour ${i + 1}`, rawData: { NOM: `Élodie ${i + 1}` }, segmentCount: 2,
    status: i === 349 ? 'FAILED' : 'VALID',
  }));

  beforeEach(() => {
    companyId.set(8);
    TestBed.configureTestingModule({ imports: [CampaignList], providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: WorkspaceService, useValue: { selectedCompanyId: companyId, refreshCompanies: vi.fn() } },
    ] });
    fixture = TestBed.createComponent(CampaignList); component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController); fixture.detectChanges();
    http.expectOne(`${API_BASE}/campaigns/company/8`).flush(campaigns); fixture.detectChanges();
    http.expectOne(`${API_BASE}/recipients/campaign/1`).flush(recipients); fixture.detectChanges();
  });
  afterEach(() => { http.verify(); fixture.destroy(); });

  it('renders 10 campaigns and 25 of 350 recipients, with every recipient reachable', () => {
    expect(fixture.nativeElement.querySelectorAll('.campaign-row')).toHaveLength(10);
    expect(fixture.nativeElement.querySelectorAll('.recipient-card')).toHaveLength(25);
    expect(component.pagedCampaigns().pageCount).toBe(4);
    expect(component.pagedRecipients().pageCount).toBe(14);
    const navLabels = Array.from(fixture.nativeElement.querySelectorAll('nav'),
      (nav: unknown) => (nav as HTMLElement).getAttribute('aria-label'));
    expect(new Set(navLabels).size).toBe(navLabels.length);
    expect(fixture.nativeElement.querySelector('button[aria-label="Page précédente des destinataires"]').disabled).toBe(true);
    const ids: number[] = [];
    for (let page = 1; page <= 14; page++) {
      component.recipientPage.set(page); fixture.detectChanges();
      ids.push(...component.pagedRecipients().items.map(r => r.id!));
      expect(component.selectedCampaignRecipients()).toBe(350);
      expect(component.totalSegments()).toBe(700);
    }
    expect(ids).toEqual(Array.from({ length: 350 }, (_, i) => i + 1));
    expect(new Set(ids).size).toBe(350);
    expect(component.pagedRecipients().start).toBe(326);
    expect(component.pagedRecipients().end).toBe(350);
    expect(fixture.nativeElement.querySelector('button[aria-label="Page suivante des destinataires"]').disabled).toBe(true);
  });

  it('navigates using the visible buttons, including the final partial campaign page', () => {
    const next: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Page suivante des destinataires"]');
    next.click(); fixture.detectChanges();
    expect(component.pagedRecipients().items[0].id).toBe(26);
    expect(document.activeElement?.id).toBe('recipient-title');
    const last: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Dernière page des campagnes"]');
    last.click(); fixture.detectChanges();
    http.expectOne(`${API_BASE}/recipients/campaign/31`).flush([]); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.campaign-row')).toHaveLength(5);
    expect(component.selectedCampaign()?.id).toBe(31);
    expect(component.recipientPage()).toBe(1);
  });

  it('searches all recipients and resets paging after name, number or status filters', () => {
    component.recipientPage.set(14); fixture.detectChanges();
    component.recipientSearch.set('elodie 350'); fixture.detectChanges();
    expect(component.recipientPage()).toBe(1);
    expect(component.filteredRecipients().map(r => r.id)).toEqual([350]);
    component.recipientSearch.set('+225 07 0000 0350'); fixture.detectChanges();
    expect(component.filteredRecipients().map(r => r.id)).toEqual([350]);
    component.recipientSearch.set(''); component.recipientStatusFilter.set('FAILED'); fixture.detectChanges();
    expect(component.filteredRecipients().map(r => r.id)).toEqual([350]);
    expect(component.selectedCampaignRecipients()).toBe(350);
    expect(component.totalSegments()).toBe(700);
  });

  it('searches all campaigns and handles zero matching results', () => {
    component.campaignPage.set(4); fixture.detectChanges();
    http.expectOne(`${API_BASE}/recipients/campaign/31`).flush([]); fixture.detectChanges();
    component.searchQuery.set('  Campagne 035  '); fixture.detectChanges();
    http.expectOne(`${API_BASE}/recipients/campaign/35`).flush([]); fixture.detectChanges();
    expect(component.filteredCampaigns().map(c => c.id)).toEqual([35]);
    expect(component.campaignPage()).toBe(1);
    component.campaignStatusFilter.set('DRAFT'); fixture.detectChanges();
    expect(component.pagedCampaigns().total).toBe(0);
    expect(component.selectedCampaign()).toBeUndefined();
    expect(component.recipients()).toHaveLength(0);
  });

  it('cancels old recipient requests when switching company and clears the old data', () => {
    component.selectCampaign(campaigns[1]);
    const old = http.expectOne(`${API_BASE}/recipients/campaign/2`);
    companyId.set(9); fixture.detectChanges();
    expect(old.cancelled).toBe(true);
    expect(component.recipients()).toHaveLength(0);
    http.expectOne(`${API_BASE}/campaigns/company/9`).flush([{ id: 900, name: 'Autre entreprise', status: 'IMPORTED' }]); fixture.detectChanges();
    http.expectOne(`${API_BASE}/recipients/campaign/900`).flush([{ id: 900, phoneNumber: '0700000900' }]); fixture.detectChanges();
    expect(component.selectedCampaign()?.id).toBe(900);
    expect(component.pagedRecipients().items.map(r => r.id)).toEqual([900]);
  });

  it('sends the whole campaign by ID even when a single failed recipient is being viewed', () => {
    component.recipientStatusFilter.set('FAILED'); fixture.detectChanges();
    expect(component.filteredRecipients()).toHaveLength(1);
    component.sendCampaign(component.selectedCampaign()!);
    const send = http.expectOne(`${API_BASE}/campaigns/1/send`);
    expect(send.request.method).toBe('POST');
    expect(send.request.body).toEqual({});
    expect(component.recipients()).toHaveLength(350);
    send.flush({ message: 'Test sans envoi réel' }, { status: 503, statusText: 'Unavailable' });
  });
});
