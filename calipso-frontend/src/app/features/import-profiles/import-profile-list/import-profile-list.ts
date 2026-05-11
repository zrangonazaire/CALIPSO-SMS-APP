import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company, DataType, ExcelVariable, ImportProfile, SmsTemplate } from '../../../core/models/api.models';
import { CompanyService } from '../../../core/services/company';
import { ExcelVariableService } from '../../../core/services/excel-variable';
import { ImportProfileService } from '../../../core/services/import-profile';
import { SmsTemplateService } from '../../../core/services/sms-template';

@Component({
  selector: 'app-import-profile-list',
  imports: [CommonModule, FormsModule],
  templateUrl: './import-profile-list.html',
  styleUrl: './import-profile-list.scss',
})
export class ImportProfileList implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly profileService = inject(ImportProfileService);
  private readonly variableService = inject(ExcelVariableService);
  private readonly templateService = inject(SmsTemplateService);

  companies: Company[] = [];
  profiles: ImportProfile[] = [];
  variables: ExcelVariable[] = [];
  templates: SmsTemplate[] = [];
  selectedCompanyId = 0;
  selectedProfileId = 0;
  activePanel: 'profile' | 'variables' | 'message' | 'review' = 'profile';
  error = '';
  success = '';
  dataTypes: DataType[] = ['TEXT', 'NUMBER', 'DATE', 'PHONE', 'EMAIL', 'BOOLEAN'];

  profileForm: ImportProfile = { companyId: 0, name: '', description: '' };
  variableForm: ExcelVariable = this.emptyVariableForm();
  templateForm: SmsTemplate = {
    profileId: 0,
    name: '',
    content: 'Bonjour {{PRENOM}}, votre message ici.',
  };

  variablePresets: Array<Pick<ExcelVariable, 'code' | 'label' | 'dataType' | 'required' | 'phone'>> = [
    { code: 'TELEPHONE', label: 'Numero de telephone', dataType: 'PHONE', required: true, phone: true },
    { code: 'PRENOM', label: 'Prenom', dataType: 'TEXT', required: false, phone: false },
    { code: 'NOM', label: 'Nom', dataType: 'TEXT', required: false, phone: false },
    { code: 'MONTANT', label: 'Montant', dataType: 'NUMBER', required: false, phone: false },
    { code: 'DATE_ECHEANCE', label: 'Date echeance', dataType: 'DATE', required: false, phone: false },
  ];

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        if (companies[0]?.id) {
          this.selectedCompanyId = companies[0].id;
          this.profileForm.companyId = companies[0].id;
          this.loadProfiles();
        }
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  get selectedProfile(): ImportProfile | undefined {
    return this.profiles.find((profile) => profile.id === this.selectedProfileId);
  }

  get phoneVariables(): ExcelVariable[] {
    return this.variables.filter((variable) => variable.phone);
  }

  get requiredVariables(): ExcelVariable[] {
    return this.variables.filter((variable) => variable.required);
  }

  get smsLength(): number {
    return this.templateForm.content.length;
  }

  get smsSegments(): number {
    if (!this.smsLength) {
      return 0;
    }
    return this.smsLength <= 160 ? 1 : Math.ceil(this.smsLength / 153);
  }

  get readinessScore(): number {
    let score = 0;
    if (this.selectedProfileId) score += 25;
    if (this.variables.length > 0) score += 20;
    if (this.phoneVariables.length > 0) score += 25;
    if (this.templates.length > 0 || this.templateForm.content.trim()) score += 20;
    if (this.templateVariables.every((code) => this.variables.some((variable) => variable.code === code))) score += 10;
    return score;
  }

  get templateVariables(): string[] {
    const matches = this.templateForm.content.match(/\{\{\s*[\w-]+\s*\}\}/g) || [];
    return [...new Set(matches.map((match) => match.replace(/[{}]/g, '').trim()))];
  }

  get unknownTemplateVariables(): string[] {
    return this.templateVariables.filter((code) => !this.variables.some((variable) => variable.code === code));
  }

  get previewMessage(): string {
    return this.templateForm.content.replace(/\{\{\s*([\w-]+)\s*\}\}/g, (_match, code) => {
      const variable = this.variables.find((item) => item.code === code);
      return variable ? this.sampleValue(variable) : `[${code}]`;
    });
  }

  loadProfiles(): void {
    this.error = '';
    this.success = '';
    this.profileForm.companyId = this.selectedCompanyId;
    this.profileService.findByCompany(this.selectedCompanyId).subscribe({
      next: (profiles) => {
        this.profiles = profiles;
        this.selectedProfileId = profiles[0]?.id || 0;
        this.selectedProfileId ? this.loadProfileWorkspace() : this.resetWorkspace();
      },
      error: () => this.error = 'Impossible de charger les profils.',
    });
  }

  loadProfileWorkspace(): void {
    this.error = '';
    this.success = '';
    this.variableForm = this.emptyVariableForm();
    this.templateForm.profileId = this.selectedProfileId;
    this.loadVariables();
    this.loadTemplates();
  }

  loadVariables(): void {
    if (!this.selectedProfileId) {
      this.variables = [];
      return;
    }
    this.variableService.findByProfile(this.selectedProfileId).subscribe({
      next: (variables) => this.variables = variables,
      error: () => this.error = 'Impossible de charger les variables du profil.',
    });
  }

  loadTemplates(): void {
    if (!this.selectedProfileId) {
      this.templates = [];
      return;
    }
    this.templateService.findByProfile(this.selectedProfileId).subscribe({
      next: (templates) => {
        this.templates = templates;
        if (!this.templateForm.name && templates[0]) {
          this.templateForm = {
            profileId: this.selectedProfileId,
            name: templates[0].name,
            content: templates[0].content,
          };
        }
      },
      error: () => this.error = 'Impossible de charger les contenus SMS.',
    });
  }

  createProfile(): void {
    this.error = '';
    this.success = '';
    this.profileForm.companyId = this.selectedCompanyId;
    this.profileService.create(this.profileForm).subscribe({
      next: (profile) => {
        this.profileForm = { companyId: this.selectedCompanyId, name: '', description: '' };
        this.success = 'Profil de campagne cree. Vous pouvez maintenant declarer les entetes et le SMS.';
        this.profileService.findByCompany(this.selectedCompanyId).subscribe({
          next: (profiles) => {
            this.profiles = profiles;
            this.selectedProfileId = profile.id || profiles[0]?.id || 0;
            this.activePanel = 'variables';
            this.loadProfileWorkspace();
          },
        });
      },
      error: () => this.error = 'Impossible de creer le profil.',
    });
  }

  createVariable(): void {
    if (!this.selectedProfileId) {
      this.error = 'Selectionnez ou creez un profil avant de declarer les entetes.';
      return;
    }
    this.error = '';
    this.success = '';
    this.variableForm.profileId = this.selectedProfileId;
    this.variableForm.code = this.normalizeCode(this.variableForm.code);
    if (this.variableForm.phone) {
      this.variableForm.required = true;
      this.variableForm.dataType = 'PHONE';
    }
    this.variableService.create(this.variableForm).subscribe({
      next: () => {
        this.success = 'Variable ajoutee au profil de campagne.';
        this.variableForm = this.emptyVariableForm();
        this.loadVariables();
      },
      error: (err) => this.error = err.error?.message || 'Impossible de creer la variable.',
    });
  }

  deleteVariable(variable: ExcelVariable): void {
    if (!variable.id) {
      return;
    }

    const confirmed = window.confirm(`Supprimer la variable ${variable.code} de ce profil ?`);
    if (!confirmed) {
      return;
    }

    this.error = '';
    this.success = '';
    this.variableService.delete(variable.id).subscribe({
      next: () => {
        this.success = 'Variable supprimee du profil.';
        this.loadVariables();
      },
      error: () => this.error = 'Impossible de supprimer la variable.',
    });
  }

  usePreset(preset: Pick<ExcelVariable, 'code' | 'label' | 'dataType' | 'required' | 'phone'>): void {
    this.variableForm = {
      profileId: this.selectedProfileId,
      code: preset.code,
      label: preset.label,
      dataType: preset.dataType,
      required: preset.required,
      phone: preset.phone,
    };
  }

  createTemplate(): void {
    if (!this.selectedProfileId) {
      this.error = 'Selectionnez ou creez un profil avant de composer le SMS.';
      return;
    }
    this.error = '';
    this.success = '';
    this.templateForm.profileId = this.selectedProfileId;
    this.templateService.create(this.templateForm).subscribe({
      next: () => {
        this.success = 'Contenu SMS enregistre pour ce profil.';
        this.templateForm = { profileId: this.selectedProfileId, name: '', content: '' };
        this.loadTemplates();
        this.activePanel = 'review';
      },
      error: () => this.error = 'Impossible de creer le contenu SMS.',
    });
  }

  insertVariable(variable: ExcelVariable): void {
    const token = `{{${variable.code}}}`;
    const separator = this.templateForm.content.trim().length ? ' ' : '';
    this.templateForm.content = `${this.templateForm.content}${separator}${token}`;
  }

  onPhoneVariableChange(): void {
    if (this.variableForm.phone) {
      this.variableForm.required = true;
      this.variableForm.dataType = 'PHONE';
    }
  }

  editTemplate(template: SmsTemplate): void {
    this.templateForm = {
      profileId: this.selectedProfileId,
      name: template.name,
      content: template.content,
    };
    this.activePanel = 'message';
  }

  private resetWorkspace(): void {
    this.variables = [];
    this.templates = [];
    this.variableForm = this.emptyVariableForm();
    this.templateForm = { profileId: 0, name: '', content: 'Bonjour {{PRENOM}}, votre message ici.' };
  }

  private emptyVariableForm(): ExcelVariable {
    return {
      profileId: this.selectedProfileId,
      code: '',
      label: '',
      dataType: 'TEXT',
      required: false,
      phone: false,
    };
  }

  private normalizeCode(value: string): string {
    return value.trim().toUpperCase().replace(/\s+/g, '_').replace(/-/g, '_');
  }

  private sampleValue(variable: ExcelVariable): string {
    const samples: Record<DataType, string> = {
      TEXT: variable.code.includes('NOM') ? 'Kouame' : 'Awa',
      NUMBER: '25000',
      DATE: '15/05/2026',
      PHONE: '+2250700000000',
      EMAIL: 'client@exemple.ci',
      BOOLEAN: 'Oui',
    };
    return samples[variable.dataType];
  }
}
