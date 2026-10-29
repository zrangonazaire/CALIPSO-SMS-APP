import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PeriodFilter } from '../../shared/period-filter/period-filter';
import { DatePeriod } from '../../core/models/date-period';
import { AuthService } from '../../core/auth/auth';
import { Campaign, CampaignChannel } from '../../core/models/api.models';
import { CampaignService } from '../../core/services/campaign';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink, PeriodFilter],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  readonly auth = inject(AuthService);
  readonly workspace = inject(WorkspaceService);
  private readonly campaignService = inject(CampaignService);

  readonly campaigns = signal<Campaign[]>([]);
  readonly error = signal('');
  readonly loading = signal(false);
  readonly period = signal<DatePeriod>({});
  readonly completedCampaigns = computed(() => this.campaigns().filter(c => c.status === 'COMPLETED').length);
  readonly readyCampaigns = computed(() => this.campaigns().filter(c => c.status === 'IMPORTED' || c.status === 'DRAFT').length);
  readonly totalSentMessages = computed(() => this.campaigns().reduce((sum, c) => sum + (c.totalSent || 0), 0));

  constructor() {
    effect(onCleanup => {
      const companyId = this.workspace.selectedCompanyId();
      const period = this.period();
      this.campaigns.set([]); this.error.set(''); this.loading.set(false);
      if (!companyId) return;
      this.loading.set(true);
      const subscription = this.campaignService.findByCompany(companyId, period).subscribe({
        next: campaigns => { this.campaigns.set(campaigns); this.loading.set(false); },
        error: () => { this.loading.set(false); this.error.set('Impossible de charger les statistiques de cette p\u00e9riode.'); },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  get activeBalance(): number {
    return this.workspace.activeCompany()?.smsBalance ?? 0;
  }

  countByChannel(ch: CampaignChannel): number {
    return this.campaigns().filter((c) => (c.channel || 'SMS') === ch).length;
  }

  statusLabel(status?: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon',
      IMPORTED: 'Prête à envoyer',
      SENDING: 'En cours',
      COMPLETED: 'Envoyée',
      FAILED: 'Échec',
    };
    return status ? labels[status] || status : 'Brouillon';
  }

  statusClass(status?: string): string {
    if (status === 'COMPLETED') return 'badge-soft-success';
    if (status === 'FAILED') return 'badge-soft-danger';
    if (status === 'IMPORTED') return 'badge-soft-primary';
    return 'badge-soft-warning';
  }

}
