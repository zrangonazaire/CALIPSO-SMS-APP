import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CompanySubscriptionSummary, PaymentMethod, SubscriptionPlan, WalletTransaction } from '../../core/models/api.models';
import { SubscriptionService } from '../../core/services/subscription';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-subscription',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Subscription {
  readonly workspace = inject(WorkspaceService);
  private readonly api = inject(SubscriptionService);
  private readonly fb = inject(FormBuilder);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('checkoutDialog');
  readonly months = signal<3 | 6 | 12>(3);
  readonly periods = [3, 6, 12] as const;
  readonly plans = signal<SubscriptionPlan[]>([]);
  readonly visiblePlans = computed(() => this.plans().filter(p => p.durationMonths === this.months() && !!p.packagePrice));
  readonly summary = signal<CompanySubscriptionSummary | undefined>(undefined);
  readonly transactions = signal<WalletTransaction[]>([]);
  readonly selectedPlan = signal<SubscriptionPlan | undefined>(undefined);
  readonly receipt = signal<WalletTransaction | undefined>(undefined);
  readonly loading = signal(false);
  readonly catalogueLoading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly activeSubscription = computed(() => this.summary()?.activeSubscription);
  readonly legacyWallet = computed(() => this.summary()?.whatsappBalance == null && this.summary()?.emailBalance == null);
  readonly checkout = this.fb.nonNullable.group({
    paymentMethod: ['WAVE_CI' as PaymentMethod, Validators.required],
    payerPhone: [''],
  });
  readonly paymentOptions: { code: PaymentMethod; name: string }[] = [
    { code: 'WAVE_CI', name: 'Wave' },
    { code: 'ORANGE_MONEY_CI', name: 'Orange Money' },
    { code: 'MTN_MOMO_CI', name: 'MTN MoMo' },
    { code: 'MOOV_MONEY_CI', name: 'Moov Money' },
    { code: 'CARD', name: 'Carte bancaire' },
  ];

  constructor() {
    this.api.findPlans().subscribe({
      next: plans => { this.plans.set(plans); this.catalogueLoading.set(false); },
      error: () => { this.catalogueLoading.set(false); this.error.set('Impossible de charger les offres. Rechargez la page.'); },
    });
    effect(onCleanup => {
      const companyId = this.workspace.selectedCompanyId();
      this.summary.set(undefined); this.transactions.set([]); this.receipt.set(undefined);
      this.selectedPlan.set(undefined); untracked(() => this.dialog()?.nativeElement.close());
      this.error.set(''); this.success.set(''); this.loading.set(false);
      this.checkout.patchValue({ payerPhone: untracked(() => this.workspace.activeCompany()?.phone) || '' });
      if (!companyId) return;
      const summary = this.api.findCompanySubscription(companyId).subscribe({
        next: value => this.summary.set(value),
        error: () => this.error.set('Impossible de charger les soldes de cette entreprise.'),
      });
      const transactions = this.api.findTransactions(companyId).subscribe({
        next: value => this.transactions.set(value),
        error: () => this.error.set('Impossible de charger les transactions.'),
      });
      onCleanup(() => { summary.unsubscribe(); transactions.unsubscribe(); });
    });
  }

  periodLabel(months: number | undefined): string { return months === 12 ? '1 an' : `${months} mois`; }

  features(plan: SubscriptionPlan): string[] {
    const base = ['SMS, WhatsApp et e-mail', 'Imports Excel / CSV et en-têtes configurables', 'Messages personnalisés, historique et statistiques'];
    if (plan.serviceTier === 'ESSENTIEL') return base;
    const pro = [...base, 'Configuration accompagnée des intégrations', 'Formation initiale et assistance prioritaire'];
    return plan.serviceTier === 'COMPLET' ? [...pro, 'Paramétrage initial des trois canaux', 'Deux séances de formation', 'Revue mensuelle des campagnes', 'Jusqu’à 4 heures d’assistance par mois'] : pro;
  }

  openPackCheckout(plan: SubscriptionPlan): void {
    if (!plan.packagePrice || !this.workspace.selectedCompanyId() || this.loading()) return;
    this.error.set(''); this.success.set(''); this.selectedPlan.set(plan);
    this.dialog()?.nativeElement.showModal();
  }

  closeCheckout(): void {
    if (this.loading()) return;
    this.dialog()?.nativeElement.close(); this.selectedPlan.set(undefined);
  }

  confirmMobilePayment(): void {
    const plan = this.selectedPlan(), companyId = this.workspace.selectedCompanyId();
    if (!plan || !companyId || this.loading()) return;
    const { paymentMethod, payerPhone } = this.checkout.getRawValue();
    if (paymentMethod !== 'CARD' && !payerPhone.trim()) {
      this.error.set('Renseignez le numéro du compte Mobile Money à utiliser.'); return;
    }
    this.loading.set(true); this.error.set(''); this.success.set('');
    this.api.subscribe(companyId, { planCode: plan.code, paymentMethod, payerPhone: payerPhone.trim() || undefined }).subscribe({
      next: summary => {
        if (companyId !== this.workspace.selectedCompanyId()) return;
        this.loading.set(false); this.summary.set(summary);
        const receipt = summary.lastTransaction || undefined;
        this.receipt.set(receipt);
        if (receipt?.paymentStatus === 'COMPLETED') {
          this.success.set(`Forfait ${plan.name} activé : les trois soldes ont été crédités.`);
        } else if (receipt?.paymentStatus === 'PENDING') {
          this.success.set('Paiement en attente. Le forfait sera activé après confirmation du paiement.');
        } else {
          this.error.set('Le paiement n’a pas été confirmé. Aucun crédit ajouté.');
        }
        this.closeCheckout();
        this.workspace.refreshCompanies(companyId);
        this.refreshBalances();
      },
      error: err => {
        if (companyId !== this.workspace.selectedCompanyId()) return;
        this.loading.set(false); this.error.set(err.error?.message || 'Impossible de souscrire à ce forfait.');
      },
    });
  }

  refreshBalances(): void {
    const companyId = this.workspace.selectedCompanyId();
    if (!companyId) return;
    this.api.findCompanySubscription(companyId).subscribe({
      next: value => { if (companyId === this.workspace.selectedCompanyId()) this.summary.set(value); },
      error: () => this.error.set('Impossible d’actualiser les soldes.'),
    });
    this.api.findTransactions(companyId).subscribe({
      next: value => { if (companyId === this.workspace.selectedCompanyId()) this.transactions.set(value); },
      error: () => this.error.set('Impossible d’actualiser les transactions.'),
    });
  }
}
