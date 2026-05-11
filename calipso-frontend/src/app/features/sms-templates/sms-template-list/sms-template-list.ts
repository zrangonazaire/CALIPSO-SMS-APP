import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company, ImportProfile, SmsTemplate } from '../../../core/models/api.models';
import { CompanyService } from '../../../core/services/company';
import { ImportProfileService } from '../../../core/services/import-profile';
import { SmsTemplateService } from '../../../core/services/sms-template';

@Component({
  selector: 'app-sms-template-list',
  imports: [CommonModule, FormsModule],
  templateUrl: './sms-template-list.html',
  styleUrl: './sms-template-list.scss',
})
export class SmsTemplateList implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly profileService = inject(ImportProfileService);
  private readonly templateService = inject(SmsTemplateService);

  companies: Company[] = [];
  profiles: ImportProfile[] = [];
  templates: SmsTemplate[] = [];
  selectedCompanyId = 0;
  profileId = 0;
  error = '';
  form: SmsTemplate = { profileId: 0, name: '', content: '' };

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
        this.profileId ? this.loadTemplates() : this.templates = [];
      },
      error: () => this.error = 'Impossible de charger les profils.',
    });
  }

  loadTemplates(): void {
    this.templateService.findByProfile(this.profileId).subscribe({
      next: (templates) => this.templates = templates,
      error: () => this.error = 'Impossible de charger les modeles.',
    });
  }

  createTemplate(): void {
    this.form.profileId = this.profileId;
    this.templateService.create(this.form).subscribe({
      next: () => {
        this.form = { profileId: this.profileId, name: '', content: '' };
        this.loadTemplates();
      },
      error: () => this.error = 'Impossible de creer le modele.',
    });
  }
}
