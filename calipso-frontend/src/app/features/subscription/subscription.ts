import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CompanySubscriptionSummary,
  PaymentMethod,
  SubscriptionPlan,
  SubscriptionPlanCode,
  WalletTransaction,
} from '../../core/models/api.models';
import { SubscriptionService } from '../../core/services/subscription';
import { WorkspaceService } from '../../core/services/workspace';

export interface MobilePaymentOption {
  code: PaymentMethod;
  name: string;
  badge: string;
  prefixHint: string;
  instruction: string;
  icon: string;
  accentColor: string;
}

@Component({
  selector: 'app-subscription',
  imports: [CommonModule, FormsModule],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
})
export class Subscription implements OnInit {
  readonly workspace = inject(WorkspaceService);
  private readonly subscriptionService = inject(SubscriptionService);

  plans: SubscriptionPlan[] = [];
  transactions: WalletTransaction[] = [];
  summary?: CompanySubscriptionSummary;
  walletBalance = 0;
  rechargeUnits = 1000;
  error = '';
  success = '';
  loading = false;

  // Guichet de paiement Mobile Money Côte d'Ivoire
  checkoutModalOpen = false;
  checkoutMode: 'RECHARGE' | 'PACK' = 'RECHARGE';
  selectedPlanForPack?: SubscriptionPlan;
  selectedPaymentMethod: PaymentMethod = 'WAVE_CI';
  payerPhone = '+225 07 08 09 10 11';
  otpCode = '';
  lastReceipt?: WalletTransaction | null;

  readonly quickAmounts = [100, 500, 1000, 2500, 5000, 10000];

  readonly paymentOptions: MobilePaymentOption[] = [
    {
      code: 'WAVE_CI',
      name: 'Wave Côte d\'Ivoire',
      badge: 'Le plus rapide · CI',
      prefixHint: '+225 07 / 05 / 01',
      instruction: 'Validation instantanée dans votre application mobile Wave CI.',
      icon: 'bi-phone-vibrate-fill',
      accentColor: '#0ea5e9',
    },
    {
      code: 'ORANGE_MONEY_CI',
      name: 'Orange Money CI',
      badge: '#144*82#',
      prefixHint: '+225 07 XX XX XX XX',
      instruction: 'Composez #144*82# sur votre ligne Orange CI ou validez le Push USSD.',
      icon: 'bi-sim-fill',
      accentColor: '#f97316',
    },
    {
      code: 'MTN_MOMO_CI',
      name: 'MTN MoMo CI',
      badge: '*133#',
      prefixHint: '+225 05 XX XX XX XX',
      instruction: 'Confirmez le retrait Mobile Money via *133# sur votre numéro MTN CI.',
      icon: 'bi-broadcast-pin',
      accentColor: '#eab308',
    },
    {
      code: 'MOOV_MONEY_CI',
      name: 'Moov Money CI',
      badge: '*155#',
      prefixHint: '+225 01 XX XX XX XX',
      instruction: 'Validez la transaction Moov Money via le menu USSD *155#.',
      icon: 'bi-telephone-outbound-fill',
      accentColor: '#2563eb',
    },
    {
      code: 'CARD',
      name: 'Carte Visa / Djamo',
      badge: '3D Secure',
      prefixHint: 'Visa · Mastercard · Djamo CI',
      instruction: 'Paiement par carte bancaire ou carte prépayée Djamo Côte d\'Ivoire.',
      icon: 'bi-credit-card-2-front-fill',
      accentColor: '#4f46e5',
    },
  ];

  constructor() {
    effect(() => {
      const companyId = this.workspace.selectedCompanyId();
      const company = this.workspace.activeCompany();
      if (company?.phone) {
        this.payerPhone = company.phone;
      }
      if (companyId) {
        this.loadSelectedCompanySubscription(companyId);
      }
    });
  }

  get selectedCompanyId(): number {
    return this.workspace.selectedCompanyId();
  }

  get estimatedSms(): number {
    return this.walletBalance;
  }

  get activeSubscription() {
    return this.summary?.activeSubscription || null;
  }

  get currentPaymentOption(): MobilePaymentOption {
    return (
      this.paymentOptions.find((item) => item.code === this.selectedPaymentMethod) ||
      this.paymentOptions[0]
    );
  }

  /**
   * Calcule en temps réel le palier dégressif, le tarif unitaire, le total en FCFA
   * et l'économie réalisée selon le volume saisi ou le pack sélectionné.
   */
  get pricingQuote(): {
    units: number;
    planCode: SubscriptionPlanCode;
    planName: string;
    unitPrice: number;
    totalFcfa: number;
    baseFcfa: number;
    savingsFcfa: number;
    durationDays: number;
    autoUpgraded: boolean;
  } {
    const rawUnits = Math.max(1, Number(this.rechargeUnits) || 100);

    if (this.checkoutModalOpen && this.checkoutMode === 'PACK' && this.selectedPlanForPack) {
      const plan = this.selectedPlanForPack;
      const units = Math.max(plan.minRechargeUnits || 100, rawUnits);
      const totalFcfa = units * plan.pricePerSms;
      const baseFcfa = units * 20;
      return {
        units,
        planCode: plan.code,
        planName: plan.name,
        unitPrice: plan.pricePerSms,
        totalFcfa,
        baseFcfa,
        savingsFcfa: Math.max(0, baseFcfa - totalFcfa),
        durationDays: plan.durationDays,
        autoUpgraded: this.activeSubscription?.planCode !== plan.code,
      };
    }

    // Détermination du meilleur palier débloqué par le volume
    let volumePlan: SubscriptionPlan | undefined;
    const sortedByPriceAsc = [...this.plans].sort((a, b) => a.pricePerSms - b.pricePerSms);
    for (const plan of sortedByPriceAsc) {
      if (rawUnits >= (plan.minRechargeUnits || 1)) {
        volumePlan = plan;
        break;
      }
    }

    const fallbackPlan: SubscriptionPlan = volumePlan ||
      this.plans.find((p) => p.code === 'ESSENTIEL') || {
        id: 1,
        code: 'ESSENTIEL',
        name: 'Essentiel',
        pricePerSms: 20,
        durationDays: 30,
        minRechargeUnits: 100,
        active: true,
      };

    const activePrice = this.activeSubscription?.pricePerSms ?? 20;
    const activeCode = (this.activeSubscription?.planCode || 'ESSENTIEL') as SubscriptionPlanCode;
    const activeName = this.activeSubscription?.planName || 'Essentiel';
    const activeDuration = this.activeSubscription?.durationDays || 30;

    let appliedCode = fallbackPlan.code;
    let appliedName = fallbackPlan.name;
    let unitPrice = fallbackPlan.pricePerSms;
    let durationDays = fallbackPlan.durationDays;
    let autoUpgraded = false;

    if (fallbackPlan.pricePerSms < activePrice) {
      autoUpgraded = true;
    } else if (activePrice < fallbackPlan.pricePerSms) {
      appliedCode = activeCode;
      appliedName = activeName;
      unitPrice = activePrice;
      durationDays = activeDuration;
    }

    const totalFcfa = rawUnits * unitPrice;
    const baseFcfa = rawUnits * 20;

    return {
      units: rawUnits,
      planCode: appliedCode,
      planName: appliedName,
      unitPrice,
      totalFcfa,
      baseFcfa,
      savingsFcfa: Math.max(0, baseFcfa - totalFcfa),
      durationDays,
      autoUpgraded,
    };
  }

  ngOnInit(): void {
    this.loadPlans();
  }

  loadPlans(): void {
    this.subscriptionService.findPlans().subscribe({
      next: (plans) => (this.plans = plans),
      error: () => (this.error = 'Impossible de charger les formules.'),
    });
  }

  openRechargeCheckout(): void {
    if (!this.selectedCompanyId || this.rechargeUnits <= 0) {
      return;
    }
    this.error = '';
    this.checkoutMode = 'RECHARGE';
    this.selectedPlanForPack = undefined;
    this.checkoutModalOpen = true;
  }

  openPackCheckout(plan: SubscriptionPlan): void {
    if (!this.selectedCompanyId) {
      return;
    }
    this.error = '';
    this.checkoutMode = 'PACK';
    this.selectedPlanForPack = plan;
    if (this.rechargeUnits < (plan.minRechargeUnits || 100)) {
      this.rechargeUnits = plan.minRechargeUnits || 100;
    }
    this.checkoutModalOpen = true;
  }

  closeCheckoutModal(): void {
    if (this.loading) return;
    this.checkoutModalOpen = false;
  }

  confirmMobilePayment(): void {
    if (!this.selectedCompanyId) {
      return;
    }

    if (this.selectedPaymentMethod !== 'CARD' && (!this.payerPhone || this.payerPhone.trim().length < 8)) {
      this.error = 'Veuillez saisir un numéro Mobile Money ivoirien valide (+225 07/05/01...).';
      return;
    }

    this.loading = true;
    this.error = '';
    this.success = '';

    const quote = this.pricingQuote;

    if (this.checkoutMode === 'PACK' && this.selectedPlanForPack) {
      this.subscriptionService
        .subscribe(this.selectedCompanyId, {
          planCode: this.selectedPlanForPack.code,
          smsUnits: quote.units,
          paymentMethod: this.selectedPaymentMethod,
          payerPhone: this.payerPhone.trim(),
          otpCode: this.otpCode.trim() || undefined,
        })
        .subscribe({
          next: (summary) => {
            this.applySummary(summary);
            this.lastReceipt = summary.lastTransaction || null;
            this.success = `Paiement ${this.currentPaymentOption.name} confirmé ! Pack ${this.selectedPlanForPack?.name} activé et +${quote.units} crédits ajoutés.`;
            this.workspace.refreshCompanies(this.selectedCompanyId);
            this.loading = false;
            this.checkoutModalOpen = false;
            this.loadTransactions();
          },
          error: (err) => {
            this.error = err?.error?.message || 'Échec du paiement Mobile Money.';
            this.loading = false;
          },
        });
      return;
    }

    this.subscriptionService
      .rechargeWallet(this.selectedCompanyId, {
        smsUnits: quote.units,
        paymentMethod: this.selectedPaymentMethod,
        payerPhone: this.payerPhone.trim(),
        otpCode: this.otpCode.trim() || undefined,
      })
      .subscribe({
        next: (summary) => {
          this.applySummary(summary);
          this.lastReceipt = summary.lastTransaction || null;
          this.success = `Paiement ${this.currentPaymentOption.name} validé ! +${quote.units} crédits ajoutés (${quote.totalFcfa.toLocaleString('fr-FR')} FCFA).`;
          this.workspace.refreshCompanies(this.selectedCompanyId);
          this.loading = false;
          this.checkoutModalOpen = false;
          this.loadTransactions();
        },
        error: (err) => {
          this.error = err?.error?.message || 'Recharge impossible.';
          this.loading = false;
        },
      });
  }

  subscribe(plan: SubscriptionPlan): void {
    this.openPackCheckout(plan);
  }

  rechargeWallet(): void {
    this.openRechargeCheckout();
  }

  loadSelectedCompanySubscription(companyId = this.selectedCompanyId): void {
    if (!companyId) return;
    this.error = '';
    this.subscriptionService.findCompanySubscription(companyId).subscribe({
      next: (summary) => {
        this.applySummary(summary);
        this.loadTransactions(companyId);
      },
      error: () => (this.error = 'Impossible de charger le portefeuille.'),
    });
  }

  loadTransactions(companyId = this.selectedCompanyId): void {
    if (!companyId) return;
    this.subscriptionService.findTransactions(companyId).subscribe({
      next: (transactions) => (this.transactions = transactions),
      error: () => (this.error = ''),
    });
  }

  isCurrentPlan(plan: SubscriptionPlan): boolean {
    return this.activeSubscription?.planCode === plan.code;
  }

  planPackCost(plan: SubscriptionPlan): number {
    return (plan.minRechargeUnits || 100) * plan.pricePerSms;
  }

  planSavingsPercent(plan: SubscriptionPlan): number {
    if (plan.pricePerSms >= 20) return 0;
    return Math.round(((20 - plan.pricePerSms) / 20) * 100);
  }

  planAudience(plan: SubscriptionPlan): string {
    const labels: Record<string, string> = {
      ESSENTIEL: 'Idéal pour démarrer vos premiers envois SMS, WhatsApp et Email sans engagement',
      STANDARD: 'Pour les commerces, écoles, cliniques et PME en Côte d\'Ivoire',
      AVANCE: 'Pour les campagnes marketing récurrentes, notifications et relances clients',
      VOLUME: 'Pour les banques, assurances, institutions et grands comptes à fort trafic',
    };
    return labels[plan.code] || 'Usage multicanal professionnel';
  }

  planFeatures(plan: SubscriptionPlan): string[] {
    const features: Record<string, string[]> = {
      ESSENTIEL: [
        'SMS CI, WhatsApp & Email inclus',
        'Import Excel / CSV auto-détecté',
        'Paiement Mobile Money instantané',
      ],
      STANDARD: [
        'Économie de 20% sur chaque envoi',
        'Bibliothèque de modèles & variables',
        'Journal comptable & reçus FCFA',
      ],
      AVANCE: [
        'Économie de 40% sur chaque envoi',
        'Boutons CTA, Médias & Aperçu direct',
        'Accusés de réception opérateurs CI',
      ],
      VOLUME: [
        'Économie maximale de 50% (10 FCFA/u)',
        'Priorité haute sur passerelle Orange CI',
        'Support technique & Sender ID dédié',
      ],
    };
    return features[plan.code] || [];
  }

  transactionTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      CREDIT: 'Rechargement',
      DEBIT: 'Consommation',
      REFUND: 'Remboursement',
    };
    return labels[type] || type;
  }

  paymentMethodLabel(method?: PaymentMethod): string {
    if (!method) return 'Système';
    const labels: Record<PaymentMethod, string> = {
      WAVE_CI: 'Wave CI',
      ORANGE_MONEY_CI: 'Orange Money',
      MTN_MOMO_CI: 'MTN MoMo',
      MOOV_MONEY_CI: 'Moov Money',
      CARD: 'Carte / Djamo',
      SYSTEM: 'Débit Campagne',
    };
    return labels[method] || method;
  }

  private applySummary(summary: CompanySubscriptionSummary): void {
    this.summary = summary;
    this.walletBalance = summary.smsBalance || 0;
  }
}
