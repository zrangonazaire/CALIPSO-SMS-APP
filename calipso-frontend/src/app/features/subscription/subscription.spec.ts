import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE } from '../../core/api';
import { CompanySubscriptionSummary, SubscriptionPlan, SubscriptionPlanCode } from '../../core/models/api.models';
import { WorkspaceService } from '../../core/services/workspace';
import { Subscription } from './subscription';

describe('Multichannel offers', () => {
  let fixture: ComponentFixture<Subscription>;
  let component: Subscription;
  let http: HttpTestingController;
  const companyId = signal(8);
  const base = `${API_BASE}/subscription`;
  const summary: CompanySubscriptionSummary = { companyId: 8, companyName: 'MAGISER', smsBalance: 41, whatsappBalance: 0, emailBalance: 0 };
  const offers: SubscriptionPlan[] = [
    ['ESSENTIEL', 3, 75000, 1500, 750, 15000], ['ESSENTIEL', 6, 135000, 3000, 1500, 30000], ['ESSENTIEL', 12, 240000, 6000, 3000, 60000],
    ['PROFESSIONNEL', 3, 180000, 6000, 1500, 45000], ['PROFESSIONNEL', 6, 330000, 12000, 3000, 90000], ['PROFESSIONNEL', 12, 600000, 24000, 6000, 180000],
    ['COMPLET', 3, 360000, 15000, 3000, 90000], ['COMPLET', 6, 660000, 30000, 6000, 180000], ['COMPLET', 12, 1200000, 60000, 12000, 360000],
  ].map((row, index) => ({
    id: index + 1, code: `${row[0]}_${row[1]}M` as SubscriptionPlanCode, name: String(row[0]),
    serviceTier: row[0] as SubscriptionPlan['serviceTier'], durationMonths: Number(row[1]), packagePrice: Number(row[2]),
    includedSms: Number(row[3]), includedWhatsapp: Number(row[4]), includedEmail: Number(row[5]),
    pricePerSms: 20, minRechargeUnits: Number(row[3]), durationDays: 90, active: true,
  }));

  beforeEach(() => {
    companyId.set(8);
    HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
    HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
    TestBed.configureTestingModule({ imports: [Subscription], providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: WorkspaceService, useValue: {
        selectedCompanyId: companyId, activeCompany: () => ({ id: companyId(), name: 'MAGISER', phone: '+2250701020304' }),
        refreshCompanies: vi.fn(),
      } },
    ] });
    fixture = TestBed.createComponent(Subscription); component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController); fixture.detectChanges();
    http.expectOne(`${base}/plans`).flush(offers);
    http.expectOne(`${base}/companies/8`).flush(summary);
    http.expectOne(`${base}/companies/8/transactions`).flush([]);
    fixture.detectChanges();
  });
  afterEach(() => { http.verify(); fixture.destroy(); });

  it('shows three offers for each calendar term and the full annual package at 1,200,000 XOF', () => {
    expect(fixture.nativeElement.querySelectorAll('.plan-card')).toHaveLength(3);
    expect(component.visiblePlans().map(p => p.packagePrice)).toEqual([75000, 180000, 360000]);
    component.months.set(6); fixture.detectChanges();
    expect(component.visiblePlans().map(p => p.packagePrice)).toEqual([135000, 330000, 660000]);
    component.months.set(12); fixture.detectChanges();
    expect(component.visiblePlans().map(p => p.packagePrice)).toEqual([240000, 600000, 1200000]);
    const full = component.visiblePlans()[2];
    expect([full.includedSms, full.includedWhatsapp, full.includedEmail]).toEqual([60000, 12000, 360000]);
  });

  it('submits only the chosen offer and payer information, without a client-supplied price or quota', () => {
    component.openPackCheckout(offers[8]); fixture.detectChanges();
    component.confirmMobilePayment(); component.confirmMobilePayment();
    const request = http.expectOne(`${base}/companies/8/subscribe`);
    expect(request.request.body).toEqual({ planCode: 'COMPLET_12M', paymentMethod: 'WAVE_CI', payerPhone: '+2250701020304' });
    request.flush({ message: 'Paiement indisponible' }, { status: 503, statusText: 'Unavailable' });
    expect(component.error()).toBe('Paiement indisponible');
    expect(component.summary()?.smsBalance).toBe(41);
    expect(component.success()).toBe('');
  });

  it('keeps pending payment separate from credited balances and offers the provider checkout link', () => {
    component.openPackCheckout(offers[0]); component.confirmMobilePayment();
    const pending = { ...summary, lastTransaction: {
      id: 1, companyId: 8, companyName: 'MAGISER', type: 'CREDIT', smsUnits: 1500, amount: 75000,
      paymentStatus: 'PENDING', reference: 'PENDING-1', checkoutUrl: 'https://checkout.example/pay', reason: 'Forfait', createdAt: '2026-10-30T12:00:00',
    } };
    http.expectOne(`${base}/companies/8/subscribe`).flush(pending);
    http.expectOne(`${base}/companies/8`).flush(summary);
    http.expectOne(`${base}/companies/8/transactions`).flush([pending.lastTransaction]);
    fixture.detectChanges();
    expect(component.success()).toContain('Paiement en attente');
    expect(component.summary()?.smsBalance).toBe(41);
    expect(fixture.nativeElement.querySelector('a[href="https://checkout.example/pay"]')).not.toBeNull();
  });

  it('ignores a payment response after switching company', () => {
    component.openPackCheckout(offers[0]); component.confirmMobilePayment();
    const previous = http.expectOne(`${base}/companies/8/subscribe`);
    companyId.set(9); fixture.detectChanges();
    http.expectOne(`${base}/companies/9`).flush({ ...summary, companyId: 9, smsBalance: 99 });
    http.expectOne(`${base}/companies/9/transactions`).flush([]);
    previous.flush({ ...summary, smsBalance: 1541 });
    expect(component.summary()?.companyId).toBe(9);
    expect(component.summary()?.smsBalance).toBe(99);
    expect(component.success()).toBe('');
  });
});
