import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Campaign, Company, Recipient } from '../../../core/models/api.models';
import { CampaignService } from '../../../core/services/campaign';
import { CompanyService } from '../../../core/services/company';
import { RecipientService } from '../../../core/services/recipient';

@Component({
  selector: 'app-campaign-list',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './campaign-list.html',
  styleUrl: './campaign-list.scss',
})
export class CampaignList implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly campaignService = inject(CampaignService);
  private readonly recipientService = inject(RecipientService);

  companies: Company[] = [];
  campaigns: Campaign[] = [];
  recipients: Recipient[] = [];
  selectedCampaign?: Campaign;
  selectedCompanyId = 0;
  error = '';
  success = '';
  previewLoading = false;

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = companies[0]?.id || 0;
        if (this.selectedCompanyId) {
          this.loadCampaigns();
        }
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  loadCampaigns(): void {
    this.error = '';
    this.success = '';
    this.campaignService.findByCompany(this.selectedCompanyId).subscribe({
      next: (campaigns) => {
        this.campaigns = campaigns;
        const selectedStillExists = campaigns.find((campaign) => campaign.id === this.selectedCampaign?.id);
        this.selectedCampaign = selectedStillExists || campaigns[0];
        this.selectedCampaign?.id ? this.loadRecipients(this.selectedCampaign) : this.recipients = [];
      },
      error: () => this.error = 'Impossible de charger les campagnes.',
    });
  }

  selectCampaign(campaign: Campaign): void {
    this.selectedCampaign = campaign;
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
        this.error = 'Impossible de charger l apercu des destinataires.';
      },
    });
  }

  importExcel(campaign: Campaign, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!campaign.id || !file) {
      return;
    }

    this.campaignService.importExcel(campaign.id, file).subscribe({
      next: (updatedCampaign) => {
        this.success = 'Fichier importe avec succes.';
        this.selectedCampaign = updatedCampaign;
        this.loadCampaigns();
      },
      error: (err) => this.error = err.error?.message || 'Import Excel impossible.',
    });

    input.value = '';
  }

  sendCampaign(campaign: Campaign): void {
    if (!campaign.id) {
      return;
    }

    this.campaignService.send(campaign.id).subscribe({
      next: (updatedCampaign) => {
        this.success = 'Campagne envoyee avec succes.';
        this.selectedCampaign = updatedCampaign;
        this.loadCampaigns();
      },
      error: (err) => this.error = err.error?.message || 'Envoi impossible.',
    });
  }

  get selectedCampaignRecipients(): number {
    return this.selectedCampaign?.totalRecipients || this.recipients.length || 0;
  }

  get validRecipients(): number {
    return this.recipients.filter((recipient) => recipient.status === 'VALID' || recipient.status === 'SENT').length;
  }

  get invalidRecipients(): number {
    return this.recipients.filter((recipient) => recipient.status === 'INVALID').length;
  }

  get totalSegments(): number {
    return this.selectedCampaign?.totalSegments || this.recipients.reduce((total, recipient) => total + (recipient.segmentCount || 0), 0);
  }

  get previewColumns(): string[] {
    const keys = this.recipients.flatMap((recipient) => Object.keys(recipient.rawData || {}));
    return [...new Set(keys)].slice(0, 5);
  }

  statusLabel(status?: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon',
      IMPORTED: 'Importee',
      SENDING: 'Envoi en cours',
      COMPLETED: 'Terminee',
      FAILED: 'Echec',
      VALID: 'Valide',
      INVALID: 'Invalide',
      SENT: 'Accepte Orange',
      PENDING: 'En attente',
    };

    return status ? labels[status] || status : '-';
  }

  statusClass(status?: string): string {
    if (status === 'COMPLETED' || status === 'VALID') {
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
