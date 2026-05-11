import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Company, CompanyUser, CompanyUserRole, ImportProfile } from '../../../core/models/api.models';
import { CompanyService } from '../../../core/services/company';
import { CompanyUserService } from '../../../core/services/company-user';
import { ImportProfileService } from '../../../core/services/import-profile';

@Component({
  selector: 'app-company-list',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './company-list.html',
  styleUrl: './company-list.scss',
})
export class CompanyList implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly userService = inject(CompanyUserService);
  private readonly profileService = inject(ImportProfileService);

  companies: Company[] = [];
  users: CompanyUser[] = [];
  profiles: ImportProfile[] = [];
  selectedCompanyId = 0;
  activeTab: 'company' | 'users' | 'profiles' = 'company';
  loading = false;
  error = '';
  success = '';

  roles: CompanyUserRole[] = ['ADMIN', 'MANAGER', 'OPERATOR', 'VIEWER'];

  form: Company = this.emptyCompanyForm();
  settingsForm: Company = this.emptyCompanyForm();
  userForm: CompanyUser = this.emptyUserForm();

  ngOnInit(): void {
    this.loadCompanies();
  }

  get selectedCompany(): Company | undefined {
    return this.companies.find((company) => company.id === this.selectedCompanyId);
  }

  loadCompanies(): void {
    this.loading = true;
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = this.selectedCompanyId || companies[0]?.id || 0;
        this.loading = false;
        this.loadCompanyContext();
      },
      error: () => {
        this.error = 'Impossible de charger les societes.';
        this.loading = false;
      },
    });
  }

  loadCompanyContext(): void {
    this.success = '';
    this.error = '';
    this.userForm.companyId = this.selectedCompanyId;
    const company = this.selectedCompany;
    this.settingsForm = company ? { ...company } : this.emptyCompanyForm();

    if (!this.selectedCompanyId) {
      this.users = [];
      this.profiles = [];
      return;
    }

    this.userService.findByCompany(this.selectedCompanyId).subscribe({
      next: (users) => this.users = users,
      error: () => this.error = 'Impossible de charger les utilisateurs.',
    });

    this.profileService.findByCompany(this.selectedCompanyId).subscribe({
      next: (profiles) => this.profiles = profiles,
      error: () => this.error = 'Impossible de charger les profils.',
    });
  }

  createCompany(): void {
    if (!this.form.name.trim()) {
      return;
    }

    this.companyService.create(this.form).subscribe({
      next: (company) => {
        this.form = this.emptyCompanyForm();
        this.selectedCompanyId = company.id || 0;
        this.success = 'Societe creee avec succes.';
        this.loadCompanies();
      },
      error: () => this.error = 'Impossible de creer la societe.',
    });
  }

  updateCompanySettings(): void {
    if (!this.selectedCompanyId || !this.settingsForm.name.trim()) {
      return;
    }

    this.companyService.update(this.selectedCompanyId, this.settingsForm).subscribe({
      next: (company) => {
        this.success = 'Parametres entreprise mis a jour.';
        this.companies = this.companies.map((item) => item.id === company.id ? company : item);
        this.settingsForm = { ...company };
      },
      error: () => this.error = 'Impossible de mettre a jour les parametres entreprise.',
    });
  }

  createUser(): void {
    this.userForm.companyId = this.selectedCompanyId;
    this.userService.create(this.userForm).subscribe({
      next: () => {
        this.userForm = this.emptyUserForm();
        this.success = 'Utilisateur cree avec succes.';
        this.loadCompanyContext();
      },
      error: (err) => this.error = err.error?.message || 'Impossible de creer l utilisateur.',
    });
  }

  toggleUser(user: CompanyUser): void {
    if (!user.id) {
      return;
    }
    this.userService.updateStatus(user.id, !user.active).subscribe({
      next: () => this.loadCompanyContext(),
      error: () => this.error = 'Impossible de changer le statut utilisateur.',
    });
  }

  private emptyCompanyForm(): Company {
    return { name: '', email: '', phone: '', senderPhone: '', address: '', contactName: '', businessType: '' };
  }

  private emptyUserForm(): CompanyUser {
    return { companyId: this.selectedCompanyId, fullName: '', username: '', email: '', password: '', phone: '', role: 'OPERATOR' };
  }
}
