import { CommonModule } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Campaign, CampaignChannel, Recipient } from '../../../core/models/api.models';
import { CampaignService } from '../../../core/services/campaign';
import { RecipientService } from '../../../core/services/recipient';
import { WorkspaceService } from '../../../core/services/workspace';

@Component({
  selector: 'app-campaign-list',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './campaign-list.html',
  styleUrl: './campaign-list.scss',
})
export class CampaignList {
  readonly workspace = inject(WorkspaceService);
  private readonly campaignService = inject(CampaignService);
  private readonly recipientService = inject(RecipientService);

  campaigns: Campaign[] = [];
  recipients: Recipient[] = [];
  selectedCampaign?: Campaign;
  channelFilter: 'ALL' | CampaignChannel = 'ALL';
  searchQuery = '';
  error = '';
  success = '';
  previewLoading = false;
  sending = false;

  constructor() {
    effect(() => {
      const companyId = this.workspace.selectedCompanyId();
      if (companyId) {
        this.loadCampaigns(companyId);
      }
    });
  }

  get filteredCampaigns(): Campaign[] {
    return this.campaigns.filter((c) => {
      const ch = c.channel || 'SMS';
      const matchesChannel = this.channelFilter === 'ALL' || ch === this.channelFilter;
      const matchesSearch =
        !this.searchQuery.trim() ||
        c.name.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        (c.description || '').toLowerCase().includes(this.searchQuery.toLowerCase());
      return matchesChannel && matchesSearch;
    });
  }

  loadCampaigns(companyId = this.workspace.selectedCompanyId()): void {
    if (!companyId) return;
    this.error = '';
    this.campaignService.findByCompany(companyId).subscribe({
      next: (campaigns) => {
        this.campaigns = campaigns;
        const selectedStillExists = campaigns.find((campaign) => campaign.id === this.selectedCampaign?.id);
        this.selectedCampaign = selectedStillExists || campaigns[0];
        this.selectedCampaign?.id ? this.loadRecipients(this.selectedCampaign) : (this.recipients = []);
      },
      error: () => (this.error = 'Impossible de charger vos campagnes.'),
    });
  }

  selectCampaign(campaign: Campaign): void {
    this.selectedCampaign = campaign;
    this.success = '';
    this.error = '';
    this.loadRecipients(campaign);
  }

  loadRecipients(campaign: Campaign): void {
    if (!campaign.id) {
      this.recipients = [];
      return;
    }

    this.previewLoading = true;
    this.recipientService.findByCampaign(campaign.id).subscribe({
      next: (recipients) => {
        this.recipients = recipients;
        this.previewLoading = false;
      },
      error: () => {
        this.previewLoading = false;
        this.error = 'Impossible de charger l\'aperçu des destinataires.';
      },
    });
  }

  importExcel(campaign: Campaign, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!campaign.id || !file) {
      return;
    }

    this.error = '';
    this.success = '';
    this.campaignService.importExcel(campaign.id, file).subscribe({
      next: (updatedCampaign) => {
        this.success = `Fichier importé avec succès : ${updatedCampaign.totalValid || 0} contact(s) prêts.`;
        this.selectedCampaign = updatedCampaign;
        this.loadCampaigns();
      },
      error: (err) => (this.error = err.error?.message || 'Import du fichier impossible.'),
    });

    input.value = '';
  }

  sendCampaign(campaign: Campaign): void {
    if (!campaign.id) {
      return;
    }

    this.sending = true;
    this.error = '';
    this.success = '';
    this.campaignService.send(campaign.id).subscribe({
      next: (updatedCampaign) => {
        this.sending = false;
        this.success = `Campagne diffusée avec succès (${updatedCampaign.totalSent || 0} messages envoyés) !`;
        this.selectedCampaign = updatedCampaign;
        this.workspace.refreshCompanies(this.workspace.selectedCompanyId());
        this.loadCampaigns();
      },
      error: (err) => {
        this.sending = false;
        this.error = err.error?.message || 'Envoi impossible.';
      },
    });
  }

  get selectedCampaignRecipients(): number {
    return this.selectedCampaign?.totalRecipients || this.recipients.length || 0;
  }

  get validRecipients(): number {
    return this.recipients.filter((r) => r.status === 'VALID' || r.status === 'SENT').length;
  }

  get invalidRecipients(): number {
    return this.recipients.filter((r) => r.status === 'INVALID').length;
  }

  get totalSegments(): number {
    return (
      this.selectedCampaign?.totalSegments ||
      this.recipients.reduce((total, r) => total + (r.segmentCount || 0), 0)
    );
  }

  get previewColumns(): string[] {
    const keys = this.recipients.flatMap((r) => Object.keys(r.rawData || {}));
    return [...new Set(keys)].slice(0, 5);
  }

  statusLabel(status?: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon',
      IMPORTED: 'Prête à envoyer',
      SENDING: 'Envoi en cours',
      COMPLETED: 'Envoyée',
      FAILED: 'Échec',
      VALID: 'Prêt',
      INVALID: 'Invalide',
      SENT: 'Envoyé',
      DELIVERED: 'Livré',
      PENDING: 'En attente',
    };
    return status ? labels[status] || status : '-';
  }

  statusClass(status?: string): string {
    if (status === 'COMPLETED' || status === 'VALID' || status === 'SENT' || status === 'DELIVERED') {
      return 'badge-soft-success';
    }
    if (status === 'INVALID' || status === 'FAILED') {
      return 'badge-soft-danger';
    }
    if (status === 'IMPORTED') {
      return 'badge-soft-primary';
    }
    return 'badge-soft-warning';
  }
}
