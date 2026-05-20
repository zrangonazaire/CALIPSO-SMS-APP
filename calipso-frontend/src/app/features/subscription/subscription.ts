import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company, CompanySubscriptionSummary, SubscriptionPlan, WalletTransaction } from '../../core/models/api.models';
import { CompanyService } from '../../core/services/company';
import { SubscriptionService } from '../../core/services/subscription';

@Component({
  selector: 'app-subscription',
  imports: [CommonModule, FormsModule],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
})
export class Subscription implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly subscriptionService = inject(SubscriptionService);

  companies: Company[] = [];
  plans: SubscriptionPlan[] = [];
  transactions: WalletTransaction[] = [];
  summary?: CompanySubscriptionSummary;
  selectedCompanyId = 0;
  walletBalance = 0;
  rechargeUnits = 1000;
  error = '';
  success = '';
  loading = false;

  get estimatedSms(): number {
    return this.walletBalance;
  }

  get activeSubscription() {
    return this.summary?.activeSubscription || null;
  }

  ngOnInit(): void {
    this.loadPlans();
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = companies[0]?.id || 0;
        this.loadSelectedCompanySubscription();
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  loadPlans(): void {
    this.subscriptionService.findPlans().subscribe({
      next: (plans) => this.plans = plans,
      error: () => this.error = 'Impossible de charger les formules.',
    });
  }

  subscribe(plan: SubscriptionPlan): void {
    if (!this.selectedCompanyId) {
      return;
    }

    this.loading = true;
    this.error = '';
    this.success = '';
    this.subscriptionService.subscribe(this.selectedCompanyId, plan.code).subscribe({
      next: (summary) => {
        this.applySummary(summary);
        this.success = `Souscription ${plan.name} activee.`;
        this.loading = false;
        this.loadTransactions();
      },
      error: () => {
        this.error = 'Souscription impossible.';
        this.loading = false;
      },
    });
  }

  rechargeWallet(): void {
    if (!this.selectedCompanyId || this.rechargeUnits <= 0) {
      return;
    }

    this.loading = true;
    this.error = '';
    this.success = '';
    this.subscriptionService.rechargeWallet(this.selectedCompanyId, this.rechargeUnits).subscribe({
      next: (summary) => {
        this.success = 'Solde SMS credite avec succes.';
        this.applySummary(summary);
        this.loading = false;
        this.loadTransactions();
      },
      error: (error) => {
        this.error = error?.error?.message || 'Recharge impossible. Verifiez la souscription active et le minimum de recharge.';
        this.loading = false;
      },
    });
  }

  loadSelectedCompanySubscription(): void {
    const company = this.companies.find((item) => item.id === this.selectedCompanyId);
    this.walletBalance = company?.smsBalance || 0;
    this.summary = undefined;
    this.transactions = [];

    if (!this.selectedCompanyId) {
      return;
    }

    this.error = '';
    this.subscriptionService.findCompanySubscription(this.selectedCompanyId).subscribe({
      next: (summary) => {
        this.applySummary(summary);
        this.loadTransactions();
      },
      error: () => this.error = 'Impossible de charger la souscription.',
    });
  }

  loadTransactions(): void {
    if (!this.selectedCompanyId) {
      return;
    }

    this.subscriptionService.findTransactions(this.selectedCompanyId).subscribe({
      next: (transactions) => this.transactions = transactions,
      error: () => this.error = 'Impossible de charger l historique du wallet.',
    });
  }

  isCurrentPlan(plan: SubscriptionPlan): boolean {
    return this.activeSubscription?.planCode === plan.code;
  }

  planAudience(plan: SubscriptionPlan): string {
    const labels: Record<string, string> = {
      ESSENTIEL: 'Premiers envois, tests et petites listes clients',
      STANDARD: 'PME, commerces, etablissements et activites locales',
      AVANCE: 'Relances regulieres, marketing client et suivi commercial',
      VOLUME: 'Forts volumes, institutions et communications recurrentes',
    };

    return labels[plan.code] || 'Usage SMS professionnel';
  }

  planFeatures(plan: SubscriptionPlan): string[] {
    const features: Record<string, string[]> = {
      ESSENTIEL: ['Envoi manuel', 'Import Excel simple', 'Assistance standard'],
      STANDARD: ['Envoi manuel', 'Campagnes planifiees', 'Historique des envois'],
      AVANCE: ['Variables personnalisees', 'Controle des destinataires', 'Suivi de performance'],
      VOLUME: ['Traitement prioritaire', 'Suivi avance', 'Accompagnement dedie'],
    };

    return features[plan.code] || [];
  }

  transactionTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      CREDIT: 'Credit',
      DEBIT: 'Debit',
      REFUND: 'Remboursement',
    };

    return labels[type] || type;
  }

  private applySummary(summary: CompanySubscriptionSummary): void {
    this.summary = summary;
    this.walletBalance = summary.smsBalance || 0;
    this.companies = this.companies.map((company) => company.id === summary.companyId ? {
      ...company,
      smsBalance: summary.smsBalance,
    } : company);
  }
}
