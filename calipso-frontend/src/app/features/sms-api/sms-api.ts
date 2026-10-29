import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { PeriodFilter } from '../../shared/period-filter/period-filter';
import { DatePeriod } from '../../core/models/date-period';
import { OrangeSmsContract, SmsApiDashboard, SmsSendHistory } from '../../core/models/api.models';
import { SmsApiService } from '../../core/services/sms-api';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-sms-api',
  imports: [CommonModule, PeriodFilter],
  templateUrl: './sms-api.html',
  styleUrl: './sms-api.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SmsApi {
  readonly workspace = inject(WorkspaceService);
  private readonly smsApiService = inject(SmsApiService);

  private readonly dashboardData = signal<SmsApiDashboard | undefined>(undefined);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly period = signal<DatePeriod>({});
  private readonly refresh = signal(0);

  constructor() {
    effect(onCleanup => {
      const companyId = this.workspace.selectedCompanyId();
      const period = this.period(); this.refresh();
      this.dashboardData.set(undefined); this.error.set(''); this.loading.set(false);
      if (!companyId) return;
      this.loading.set(true);
      const subscription = this.smsApiService.findDashboard(companyId, period).subscribe({
        next: dashboard => { this.dashboardData.set(dashboard); this.loading.set(false); },
        error: err => { this.loading.set(false); this.error.set(this.errorMessage(err)); },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  get dashboard(): SmsApiDashboard | undefined { return this.dashboardData(); }
  get selectedCompanyId(): number { return this.workspace.selectedCompanyId(); }
  loadDashboard(): void { this.refresh.update(value => value + 1); }

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
