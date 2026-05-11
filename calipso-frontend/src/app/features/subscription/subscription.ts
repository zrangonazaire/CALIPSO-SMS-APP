import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company } from '../../core/models/api.models';
import { CompanyService } from '../../core/services/company';

interface SmsPlan {
  code: string;
  name: string;
  pricePerSms: number;
  durationDays: number;
  audience: string;
  features: string[];
  recommended?: boolean;
}

@Component({
  selector: 'app-subscription',
  imports: [CommonModule, FormsModule],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
})
export class Subscription implements OnInit {
  private readonly companyService = inject(CompanyService);

  companies: Company[] = [];
  selectedCompanyId = 0;
  walletBalance = 0;
  rechargeUnits = 1000;
  activePlanCode = 'avance';
  activeSince = '08/05/2026';
  error = '';
  success = '';

  plans: SmsPlan[] = [
    {
      code: 'essentiel',
      name: 'Essentiel',
      pricePerSms: 20,
      durationDays: 30,
      audience: 'Premiers envois, tests et petites listes clients',
      features: ['Envoi manuel', 'Import Excel simple', 'Assistance standard'],
    },
    {
      code: 'standard',
      name: 'Standard',
      pricePerSms: 16,
      durationDays: 60,
      audience: 'PME, commerces, etablissements et activites locales',
      features: ['Envoi manuel', 'Campagnes planifiees', 'Historique des envois'],
    },
    {
      code: 'avance',
      name: 'Avance',
      pricePerSms: 12,
      durationDays: 120,
      audience: 'Relances regulieres, marketing client et suivi commercial',
      features: ['Variables personnalisees', 'Controle des destinataires', 'Suivi de performance'],
      recommended: true,
    },
    {
      code: 'volume',
      name: 'Volume',
      pricePerSms: 10,
      durationDays: 180,
      audience: 'Forts volumes, institutions et communications recurrentes',
      features: ['Traitement prioritaire', 'Suivi avance', 'Accompagnement dedie'],
    },
  ];

  get activePlan(): SmsPlan {
    return this.plans.find((plan) => plan.code === this.activePlanCode) || this.plans[0];
  }

  get estimatedSms(): number {
    return this.walletBalance;
  }

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = companies[0]?.id || 0;
        this.syncSelectedCompanyBalance();
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  subscribe(plan: SmsPlan): void {
    this.activePlanCode = plan.code;
  }

  rechargeWallet(): void {
    if (!this.selectedCompanyId || this.rechargeUnits <= 0) {
      return;
    }

    this.companyService.rechargeWallet(this.selectedCompanyId, this.rechargeUnits).subscribe({
      next: (company) => {
        this.success = 'Solde SMS credite avec succes.';
        this.walletBalance = company.smsBalance || 0;
        this.companies = this.companies.map((item) => item.id === company.id ? company : item);
      },
      error: () => this.error = 'Recharge impossible.',
    });
  }

  syncSelectedCompanyBalance(): void {
    const company = this.companies.find((item) => item.id === this.selectedCompanyId);
    this.walletBalance = company?.smsBalance || 0;
  }
}
