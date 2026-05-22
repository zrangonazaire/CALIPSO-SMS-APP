import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company, OrangeSmsContract, SmsApiDashboard, SmsSendHistory } from '../../core/models/api.models';
import { CompanyService } from '../../core/services/company';
import { SmsApiService } from '../../core/services/sms-api';

@Component({
  selector: 'app-sms-api',
  imports: [CommonModule, FormsModule],
  templateUrl: './sms-api.html',
  styleUrl: './sms-api.scss',
})
export class SmsApi implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly smsApiService = inject(SmsApiService);

  companies: Company[] = [];
  selectedCompanyId = 0;
  dashboard?: SmsApiDashboard;
  loading = false;
  error = '';

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = companies[0]?.id || 0;
        if (this.selectedCompanyId) {
          this.loadDashboard();
        }
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  loadDashboard(): void {
    if (!this.selectedCompanyId) {
      return;
    }

    this.loading = true;
    this.error = '';
    this.smsApiService.findDashboard(this.selectedCompanyId).subscribe({
      next: (dashboard) => {
        this.dashboard = dashboard;
        this.syncCompanyBalance(dashboard);
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = this.errorMessage(err);
      },
    });
  }

  get activeContracts(): OrangeSmsContract[] {
    return this.dashboard?.contracts.filter((contract) => contract.status === 'ACTIVE') || [];
  }

  get expiredContracts(): OrangeSmsContract[] {
    return this.dashboard?.contracts.filter((contract) => contract.status !== 'ACTIVE') || [];
  }

  get latestMessages(): SmsSendHistory[] {
    return this.dashboard?.recentMessages || [];
  }

  get deliveryGap(): number {
    if (!this.dashboard) {
      return 0;
    }

    return Math.max(this.dashboard.acceptedMessages - this.dashboard.deliveredMessages, 0);
  }

  statusLabel(status?: string): string {
    const labels: Record<string, string> = {
      ACTIVE: 'Actif',
      EXPIRED: 'Expire',
      SENT: 'Accepte Orange',
      DELIVERED: 'Livre',
      FAILED: 'Echec',
      VALID: 'Valide',
      INVALID: 'Invalide',
      PENDING: 'En attente',
    };

    return labels[status || ''] || status || '-';
  }

  statusClass(status?: string): string {
    if (status === 'ACTIVE' || status === 'DELIVERED') {
      return 'badge-soft-success';
    }

    if (status === 'FAILED' || status === 'INVALID' || status === 'EXPIRED') {
      return 'badge-soft-danger';
    }

    return 'badge-soft-warning';
  }

  sourceLabel(source: string): string {
    return source === 'CAMPAIGN' ? 'Campagne' : 'Envoi cible';
  }

  private syncCompanyBalance(dashboard: SmsApiDashboard): void {
    this.companies = this.companies.map((company) => {
      if (company.id !== dashboard.companyId) {
        return company;
      }

      return {
        ...company,
        smsBalance: dashboard.remainingSmsBalance,
        senderPhone: dashboard.senderPhone,
      };
    });
  }

  private errorMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null && 'error' in err) {
      const body = (err as { error?: { detail?: string; message?: string; error?: string } }).error;
      return body?.detail || body?.message || body?.error || 'Impossible de charger la supervision API SMS.';
    }

    return 'Impossible de charger la supervision API SMS.';
  }
}
