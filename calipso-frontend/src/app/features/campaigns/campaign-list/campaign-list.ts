import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Campaign, Company } from '../../../core/models/api.models';
import { CampaignService } from '../../../core/services/campaign';
import { CompanyService } from '../../../core/services/company';

@Component({
  selector: 'app-campaign-list',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './campaign-list.html',
  styleUrl: './campaign-list.scss',
})
export class CampaignList implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly campaignService = inject(CampaignService);

  companies: Company[] = [];
  campaigns: Campaign[] = [];
  selectedCompanyId = 0;
  error = '';
  success = '';

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
      next: (campaigns) => this.campaigns = campaigns,
      error: () => this.error = 'Impossible de charger les campagnes.',
    });
  }

  importExcel(campaign: Campaign, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!campaign.id || !file) {
      return;
    }

    this.campaignService.importExcel(campaign.id, file).subscribe({
      next: () => {
        this.success = 'Fichier importe avec succes.';
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
      next: () => {
        this.success = 'Campagne envoyee avec succes.';
        this.loadCampaigns();
      },
      error: (err) => this.error = err.error?.message || 'Envoi impossible.',
    });
  }
}
