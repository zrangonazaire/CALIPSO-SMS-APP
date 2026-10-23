import { CommonModule } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OrangeSmsContract, SmsApiDashboard, SmsSendHistory } from '../../core/models/api.models';
import { SmsApiService } from '../../core/services/sms-api';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-sms-api',
  imports: [CommonModule, FormsModule],
  templateUrl: './sms-api.html',
  styleUrl: './sms-api.scss',
})
export class SmsApi {
  readonly workspace = inject(WorkspaceService);
  private readonly smsApiService = inject(SmsApiService);

  dashboard?: SmsApiDashboard;
  loading = false;
  error = '';

  constructor() {
    effect(() => {
      const companyId = this.workspace.selectedCompanyId();
      if (companyId) {
        this.loadDashboard(companyId);
      }
    });
  }

  get selectedCompanyId(): number {
    return this.workspace.selectedCompanyId();
  }

  loadDashboard(companyId = this.selectedCompanyId): void {
    if (!companyId) return;

    this.loading = true;
    this.error = '';
    this.smsApiService.findDashboard(companyId).subscribe({
      next: (dashboard) => {
        this.dashboard = dashboard;
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
      ACTIVE: 'Connecté',
      EXPIRED: 'Expiré',
      SENT: 'Envoyé',
      DELIVERED: 'Livré',
      FAILED: 'Échec',
      VALID: 'Prêt',
      INVALID: 'Invalide',
      PENDING: 'En attente',
    };
    return labels[status || ''] || status || '-';
  }

  statusClass(status?: string): string {
    if (status === 'ACTIVE' || status === 'DELIVERED' || status === 'SENT') {
      return 'badge-soft-success';
    }
    if (status === 'FAILED' || status === 'INVALID' || status === 'EXPIRED') {
      return 'badge-soft-danger';
    }
    return 'badge-soft-warning';
  }

  sourceLabel(source: string): string {
    return source === 'CAMPAIGN' ? 'Campagne' : 'Envoi Express';
  }

  private errorMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null && 'error' in err) {
      const body = (err as { error?: { detail?: string; message?: string; error?: string } }).error;
      return body?.detail || body?.message || body?.error || 'Impossible de charger l\'état des connecteurs.';
    }
    return 'Impossible de charger l\'état des connecteurs.';
  }
}
