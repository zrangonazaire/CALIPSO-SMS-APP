import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Campaign, Company, Recipient } from '../../core/models/api.models';
import { CampaignService } from '../../core/services/campaign';
import { CompanyService } from '../../core/services/company';
import { RecipientService } from '../../core/services/recipient';

@Component({
  selector: 'app-recipient-list',
  imports: [CommonModule, FormsModule],
  templateUrl: './recipient-list.html',
  styleUrl: './recipient-list.scss',
})
export class RecipientList implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly campaignService = inject(CampaignService);
  private readonly recipientService = inject(RecipientService);

  companies: Company[] = [];
  campaigns: Campaign[] = [];
  recipients: Recipient[] = [];
  selectedCompanyId = 0;
  campaignId = 0;
  error = '';

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
    this.campaignService.findByCompany(this.selectedCompanyId).subscribe({
      next: (campaigns) => {
        this.campaigns = campaigns;
        this.campaignId = campaigns[0]?.id || 0;
        this.campaignId ? this.loadRecipients() : this.recipients = [];
      },
      error: () => this.error = 'Impossible de charger les campagnes.',
    });
  }

  loadRecipients(): void {
    this.recipientService.findByCampaign(this.campaignId).subscribe({
      next: (recipients) => this.recipients = recipients,
      error: () => this.error = 'Impossible de charger les destinataires.',
    });
  }
}
