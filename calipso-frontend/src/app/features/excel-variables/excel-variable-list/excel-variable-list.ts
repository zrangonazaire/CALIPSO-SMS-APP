import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company, DataType, ExcelVariable, ImportProfile } from '../../../core/models/api.models';
import { CompanyService } from '../../../core/services/company';
import { ExcelVariableService } from '../../../core/services/excel-variable';
import { ImportProfileService } from '../../../core/services/import-profile';

@Component({
  selector: 'app-excel-variable-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './excel-variable-list.html',
  styleUrl: './excel-variable-list.scss',
})
export class ExcelVariableList implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly profileService = inject(ImportProfileService);
  private readonly excelVariableService = inject(ExcelVariableService);

  companies: Company[] = [];
  profiles: ImportProfile[] = [];
  variables: ExcelVariable[] = [];
  selectedCompanyId = 0;
  profileId = 0;
  error = '';

  dataTypes: DataType[] = ['TEXT', 'NUMBER', 'DATE', 'PHONE', 'EMAIL', 'BOOLEAN'];

  form: ExcelVariable = {
    profileId: this.profileId,
    code: '',
    label: '',
    dataType: 'TEXT',
    required: false,
    phone: false,
  };

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
        this.form.profileId = this.profileId;
        this.profileId ? this.loadVariables() : this.variables = [];
      },
      error: () => this.error = 'Impossible de charger les profils.',
    });
  }

  loadVariables(): void {
    this.form.profileId = this.profileId;
    this.excelVariableService.findByProfile(this.profileId).subscribe({
      next: (data) => this.variables = data,
      error: () => this.error = 'Impossible de charger les variables.',
    });
  }

  createVariable(): void {
    this.form.profileId = this.profileId;
    this.excelVariableService.create(this.form).subscribe({
      next: () => {
        this.form = {
          profileId: this.profileId,
          code: '',
          label: '',
          dataType: 'TEXT',
          required: false,
          phone: false,
        };
        this.loadVariables();
      },
      error: (err) => this.error = err.error?.message || 'Erreur lors de la creation de la variable.',
    });
  }
}
