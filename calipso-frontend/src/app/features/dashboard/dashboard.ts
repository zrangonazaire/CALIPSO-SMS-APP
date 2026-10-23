import { CommonModule } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { Campaign, CampaignChannel } from '../../core/models/api.models';
import { CampaignService } from '../../core/services/campaign';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  readonly auth = inject(AuthService);
  readonly workspace = inject(WorkspaceService);
  private readonly campaignService = inject(CampaignService);

  campaigns: Campaign[] = [];
  error = '';

  constructor() {
    effect(() => {
      const companyId = this.workspace.selectedCompanyId();
      if (companyId) {
        this.loadCampaigns(companyId);
      }
    });
  }

  get activeBalance(): number {
    return this.workspace.activeCompany()?.smsBalance ?? 0;
  }

  get completedCampaigns(): number {
    return this.campaigns.filter((c) => c.status === 'COMPLETED').length;
  }

  get readyCampaigns(): number {
    return this.campaigns.filter((c) => c.status === 'IMPORTED' || c.status === 'DRAFT').length;
  }

  get totalSentMessages(): number {
    return this.campaigns.reduce((sum, c) => sum + (c.totalSent || 0), 0);
  }

  countByChannel(ch: CampaignChannel): number {
    return this.campaigns.filter((c) => (c.channel || 'SMS') === ch).length;
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

  private loadCampaigns(companyId: number): void {
    this.campaignService.findByCompany(companyId).subscribe({
      next: (campaigns) => (this.campaigns = campaigns),
      error: () => (this.error = 'Impossible de charger les campagnes récentes.'),
    });
  }
}
