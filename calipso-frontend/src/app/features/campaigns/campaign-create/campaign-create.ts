import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Campaign, Company, ExcelVariable, ImportProfile, SmsTemplate } from '../../../core/models/api.models';
import { CampaignService } from '../../../core/services/campaign';
import { CompanyService } from '../../../core/services/company';
import { ExcelVariableService } from '../../../core/services/excel-variable';
import { ImportProfileService } from '../../../core/services/import-profile';
import { SmsTemplateService } from '../../../core/services/sms-template';

@Component({
  selector: 'app-campaign-create',
  imports: [CommonModule, FormsModule],
  templateUrl: './campaign-create.html',
  styleUrl: './campaign-create.scss',
})
export class CampaignCreate implements OnInit {
  private readonly router = inject(Router);
  private readonly companyService = inject(CompanyService);
  private readonly profileService = inject(ImportProfileService);
  private readonly variableService = inject(ExcelVariableService);
  private readonly templateService = inject(SmsTemplateService);
  private readonly campaignService = inject(CampaignService);

  companies: Company[] = [];
  profiles: ImportProfile[] = [];
  variables: ExcelVariable[] = [];
  templates: SmsTemplate[] = [];
  selectedCompanyId = 0;
  profileId = 0;
  error = '';
  form: Campaign = { profileId: 0, templateId: 0, phoneVariableId: 0, name: '', description: '' };

  get recipientVariables(): ExcelVariable[] {
    return this.variables.filter((variable) => variable.phone);
  }

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = companies[0]?.id || 0;
        if (this.selectedCompanyId) {
          this.loadProfiles();
        }
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  loadProfiles(): void {
    this.profileService.findByCompany(this.selectedCompanyId).subscribe({
      next: (profiles) => {
        this.profiles = profiles;
        this.profileId = profiles[0]?.id || 0;
        this.loadProfileData();
      },
      error: () => this.error = 'Impossible de charger les profils.',
    });
  }

  loadProfileData(): void {
    this.form.profileId = this.profileId;
    if (!this.profileId) {
      this.variables = [];
      this.templates = [];
      return;
    }
    this.variableService.findByProfile(this.profileId).subscribe((variables) => {
      this.variables = variables;
      this.form.phoneVariableId = this.recipientVariables[0]?.id || 0;
    });
    this.templateService.findByProfile(this.profileId).subscribe((templates) => {
      this.templates = templates;
      this.form.templateId = templates[0]?.id || 0;
    });
  }

  createCampaign(): void {
    this.form.profileId = this.profileId;
    if (!this.form.phoneVariableId) {
      this.error = 'Definissez d abord une variable Excel comme destinataire SMS.';
      return;
    }

    this.campaignService.create(this.form).subscribe({
      next: () => this.router.navigateByUrl('/campaigns'),
      error: () => this.error = 'Impossible de creer la campagne.',
    });
  }
}
