import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Campaign, Company } from '../../core/models/api.models';
import { CampaignService } from '../../core/services/campaign';
import { CompanyService } from '../../core/services/company';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly campaignService = inject(CampaignService);

  companies: Company[] = [];
  campaigns: Campaign[] = [];
  error = '';

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        const firstCompanyId = companies[0]?.id;
        if (firstCompanyId) {
          this.loadCampaigns(firstCompanyId);
        }
      },
      error: () => this.error = 'Impossible de charger les donnees du tableau de bord.',
    });
  }

  get totalSmsBalance(): number {
    return this.companies.reduce((total, company) => total + (company.smsBalance || 0), 0);
  }

  get completedCampaigns(): number {
    return this.campaigns.filter((campaign) => campaign.status === 'COMPLETED').length;
  }

  get importedCampaigns(): number {
    return this.campaigns.filter((campaign) => campaign.status === 'IMPORTED').length;
  }

  get sentSms(): number {
    return this.campaigns.reduce((total, campaign) => total + (campaign.totalSent || 0), 0);
  }

  get setupSteps(): Array<{ label: string; detail: string; link: string; done: boolean }> {
    return [
      {
        label: 'Creer une societe',
        detail: 'Un compte client porte le solde SMS et les campagnes.',
        link: '/companies',
        done: this.companies.length > 0,
      },
      {
        label: 'Crediter le solde SMS',
        detail: 'Ajoutez des unites avant tout envoi reel.',
        link: '/subscription',
        done: this.totalSmsBalance > 0,
      },
      {
        label: 'Creer un profil de campagne',
        detail: 'Regroupez le format Excel, les variables et les contenus SMS.',
        link: '/import-profiles',
        done: false,
      },
      {
        label: 'Configurer les colonnes Excel',
        detail: 'Declarez les colonnes, dont la variable destinataire SMS.',
        link: '/excel-variables',
        done: false,
      },
      {
        label: 'Creer et envoyer une campagne',
        detail: 'Importez Excel, controlez les destinataires, puis envoyez.',
        link: '/campaigns/create',
        done: this.campaigns.length > 0,
      },
    ];
  }

  private loadCampaigns(companyId: number): void {
    this.campaignService.findByCompany(companyId).subscribe({
      next: (campaigns) => this.campaigns = campaigns,
      error: () => this.error = 'Impossible de charger les campagnes recentes.',
    });
  }
}
