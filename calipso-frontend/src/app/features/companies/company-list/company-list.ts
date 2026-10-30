import { CommonModule } from '@angular/common';
import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { switchMap } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../core/auth/auth';
import {
  Company,
  CompanyIntegrationSettings,
  CompanyUser,
  CompanyUserRole,
  ImportProfile,
  PlatformOverview,
  SmtpConnectionStatus,
} from '../../../core/models/api.models';
import { CompanyService } from '../../../core/services/company';
import { CompanyIntegrationService } from '../../../core/services/company-integration';
import { CompanyUserService } from '../../../core/services/company-user';
import { ImportProfileService } from '../../../core/services/import-profile';
import { PlatformService } from '../../../core/services/platform';
import { WorkspaceService } from '../../../core/services/workspace';
import { OrangeConnection } from '../orange-connection/orange-connection';

@Component({
  selector: 'app-company-list',
  imports: [CommonModule, FormsModule, OrangeConnection],
  templateUrl: './company-list.html',
  styleUrl: './company-list.scss',
})
export class CompanyList {
  readonly auth = inject(AuthService);
  readonly workspace = inject(WorkspaceService);
  private readonly companyService = inject(CompanyService);
  private readonly userService = inject(CompanyUserService);
  private readonly profileService = inject(ImportProfileService);
  private readonly integrationService = inject(CompanyIntegrationService);
  private readonly platformService = inject(PlatformService);
  private readonly destroyRef = inject(DestroyRef);

  users: CompanyUser[] = [];
  profiles: ImportProfile[] = [];
  activeTab: 'company' | 'users' | 'security' | 'admin-companies' | 'integrations' = 'company';
  loading = false;
  error = '';
  success = '';

  roles: CompanyUserRole[] = ['ADMIN', 'MANAGER', 'OPERATOR', 'VIEWER'];

  form: Company = this.emptyCompanyForm();
  settingsForm: Company = this.emptyCompanyForm();
  userForm: CompanyUser = this.emptyUserForm();

  passwordForm = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  };

  resetTargetUser?: CompanyUser;
  adminResetPasswordValue = '';
  platformOverview?: PlatformOverview;
  integrationSettings: CompanyIntegrationSettings = this.emptyIntegrationSettings();
  readonly integrationLoading = signal(false);
  readonly integrationSaving = signal(false);
  readonly integrationReady = signal(false);
  private integrationGeneration = 0;
  private smtpGeneration = 0;
  readonly smtpTesting = signal(false);
  readonly smtpResult = signal<SmtpConnectionStatus | undefined>(undefined);

  private resetSmtpTest(): void {
    this.smtpGeneration++;
    this.smtpTesting.set(false);
    this.smtpResult.set(undefined);
  }

  testSavedSmtp(): void {
    if (!this.workspace.isPlatformAdmin() || !this.integrationReady() || this.integrationLoading()
        || this.integrationSaving() || this.smtpTesting() || !this.selectedCompanyId) return;
    const companyId = this.selectedCompanyId;
    const generation = ++this.smtpGeneration;
    this.smtpTesting.set(true);
    this.smtpResult.set(undefined);
    this.integrationService.testSmtp(companyId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (companyId !== this.selectedCompanyId || generation !== this.smtpGeneration) return;
        this.smtpTesting.set(false); this.smtpResult.set(result);
      },
      error: err => {
        if (companyId !== this.selectedCompanyId || generation !== this.smtpGeneration) return;
        this.smtpTesting.set(false);
        this.smtpResult.set({ connected: false, security: '', message: err.error?.message || 'Impossible de tester la connexion SMTP enregistrée.' });
      },
    });
  }

  constructor() {
    inject(ActivatedRoute).queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.activeTab = params.get('tab') === 'integrations' ? 'integrations' : 'company';
    });
    effect(() => {
      const companyId = this.workspace.selectedCompanyId();
      if (companyId) {
        this.loadCompanyContext(companyId);
      }
    });

  }

  loadPlatformOverview(): void {
    this.platformService.getOverview().subscribe({
      next: (overview) => (this.platformOverview = overview),
      error: () => (this.error = 'Impossible de charger les statistiques de la plateforme.'),
    });
  }

  saveIntegrations(): void {
    if (!this.workspace.isPlatformAdmin() || !this.selectedCompanyId || !this.integrationReady()
        || this.integrationLoading() || this.integrationSaving()
        || this.integrationSettings.companyId !== this.selectedCompanyId) return;
    const companyId = this.selectedCompanyId;
    const companyName = this.selectedCompany?.name || 'l’entreprise sélectionnée';
    const generation = this.integrationGeneration;
    this.resetSmtpTest();
    this.error = '';
    this.success = '';
    this.integrationSaving.set(true);
    let saved = false;
    this.integrationService.save(companyId, { ...this.integrationSettings }).pipe(
      switchMap(() => {
        saved = true;
        return this.integrationService.get(companyId);
      }),
    ).subscribe({
      next: (settings) => {
        if (companyId !== this.selectedCompanyId || generation !== this.integrationGeneration) return;
        this.integrationSaving.set(false);
        this.integrationSettings = {
          ...settings,
          orangeClientId: undefined,
          orangeClientSecret: undefined,
          whatsappAccessToken: undefined,
          smtpPassword: undefined,
        };
        this.success = `Configuration enregistrée et relue pour ${companyName}. Les champs secrets restent masqués.`;
      },
      error: (err) => {
        if (companyId !== this.selectedCompanyId || generation !== this.integrationGeneration) return;
        this.integrationSaving.set(false);
        this.error = saved
          ? 'Enregistrement effectué, mais la relecture a échoué. Rechargez la configuration pour vérifier les valeurs enregistrées.'
          : err.error?.message || 'Impossible d’enregistrer les intégrations.';
      },
    });
  }

  get companies(): Company[] {
    return this.workspace.companies();
  }

  get selectedCompanyId(): number {
    return this.workspace.selectedCompanyId();
  }

  get selectedCompany(): Company | undefined {
    return this.workspace.activeCompany();
  }

  selectCompany(companyId: number): void {
    this.workspace.selectCompany(companyId);
  }

  loadCompanyContext(companyId = this.selectedCompanyId): void {
    this.success = '';
    this.error = '';
    this.userForm.companyId = companyId;
    const company = this.selectedCompany;
    this.settingsForm = company ? { ...company } : this.emptyCompanyForm();
    this.integrationSettings = this.emptyIntegrationSettings(companyId);
    this.resetSmtpTest();
    const generation = ++this.integrationGeneration;
    this.integrationReady.set(false);
    this.integrationLoading.set(false);
    this.integrationSaving.set(false);

    if (!companyId) {
      this.users = [];
      this.profiles = [];
      return;
    }

    this.userService.findByCompany(companyId).subscribe({
      next: (users) => (this.users = users),
      error: () => (this.error = 'Impossible de charger les membres de l\'équipe.'),
    });

    this.profileService.findByCompany(companyId).subscribe({
      next: (profiles) => (this.profiles = profiles),
      error: () => (this.error = ''),
    });

    if (this.workspace.isPlatformAdmin()) {
      this.integrationLoading.set(true);
      this.integrationService.get(companyId).subscribe({
        next: (settings) => {
          if (companyId !== this.selectedCompanyId || generation !== this.integrationGeneration) return;
          this.integrationSettings = { ...settings };
          this.integrationLoading.set(false);
          this.integrationReady.set(true);
        },
        error: () => {
          if (companyId !== this.selectedCompanyId || generation !== this.integrationGeneration) return;
          this.integrationLoading.set(false);
          this.error = 'Impossible de charger les intégrations de cette entreprise. Rechargez la page avant de modifier la configuration.';
        },
      });
      this.loadPlatformOverview();
    }
  }

  createCompany(): void {
    if (!this.form.name.trim()) {
      return;
    }

    this.companyService.create(this.form).subscribe({
      next: (company) => {
        this.form = this.emptyCompanyForm();
        this.success = 'Nouvel espace entreprise créé avec succès.';
        this.workspace.refreshCompanies(company.id);
      },
      error: () => (this.error = 'Impossible de créer l\'espace entreprise.'),
    });
  }

  updateCompanySettings(): void {
    if (!this.selectedCompanyId || !this.settingsForm.name.trim()) {
      return;
    }

    this.companyService.update(this.selectedCompanyId, this.settingsForm).subscribe({
      next: (company) => {
        this.success = 'Paramètres de votre espace enregistrés.';
        this.settingsForm = { ...company };
        this.workspace.refreshCompanies(company.id);
      },
      error: () => (this.error = 'Impossible de mettre à jour les paramètres.'),
    });
  }

  createUser(): void {
    this.userForm.companyId = this.selectedCompanyId;
    this.userService.create(this.userForm).subscribe({
      next: () => {
        this.userForm = this.emptyUserForm();
        this.success = 'Membre ajouté à votre équipe avec succès.';
        this.loadCompanyContext();
      },
      error: (err) => (this.error = err.error?.message || 'Impossible de créer l\'utilisateur.'),
    });
  }

  toggleUser(user: CompanyUser): void {
    if (!user.id) {
      return;
    }
    this.userService.updateStatus(user.id, !user.active).subscribe({
      next: () => this.loadCompanyContext(),
      error: () => (this.error = 'Impossible de modifier le statut du membre.'),
    });
  }

  changeUserRole(user: CompanyUser, role: CompanyUserRole): void {
    if (!user.id) {
      return;
    }
    this.userService.updateRole(user.id, role).subscribe({
      next: () => {
        this.success = `Rôle de ${user.fullName} mis à jour (${role}).`;
        this.loadCompanyContext();
      },
      error: () => (this.error = 'Impossible de modifier le rôle.'),
    });
  }

  openAdminReset(user: CompanyUser): void {
    this.resetTargetUser = user;
    this.adminResetPasswordValue = '';
    this.error = '';
    this.success = '';
  }

  confirmAdminReset(): void {
    if (!this.resetTargetUser?.id || !this.adminResetPasswordValue.trim()) {
      return;
    }
    this.userService.resetPassword(this.resetTargetUser.id, this.adminResetPasswordValue.trim()).subscribe({
      next: (res) => {
        this.success = res.message;
        this.resetTargetUser = undefined;
        this.adminResetPasswordValue = '';
      },
      error: (err) => {
        this.error = err.error?.message || 'Impossible de réinitialiser ce mot de passe.';
      },
    });
  }

  deleteUser(user: CompanyUser): void {
    if (!user.id) {
      return;
    }
    this.userService.delete(user.id).subscribe({
      next: () => {
        this.success = `Collaborateur ${user.fullName} retiré de l'espace.`;
        this.loadCompanyContext();
      },
      error: () => (this.error = 'Impossible de supprimer ce collaborateur.'),
    });
  }

  updateMyPassword(): void {
    this.error = '';
    this.success = '';

    if (!this.passwordForm.currentPassword || !this.passwordForm.newPassword) {
      this.error = 'Renseignez votre mot de passe actuel et le nouveau mot de passe.';
      return;
    }
    if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
      this.error = 'La confirmation du nouveau mot de passe ne correspond pas.';
      return;
    }

    this.auth
      .changePassword({
        currentPassword: this.passwordForm.currentPassword,
        newPassword: this.passwordForm.newPassword,
      })
      .subscribe({
        next: (res) => {
          this.success = res.message;
          this.passwordForm = { currentPassword: '', newPassword: '', confirmPassword: '' };
        },
        error: (err) => {
          this.error = err.error?.message || 'Mot de passe actuel incorrect.';
        },
      });
  }

  private emptyCompanyForm(): Company {
    return { name: '', email: '', phone: '', senderPhone: '', address: '', contactName: '', businessType: '' };
  }

  private emptyUserForm(): CompanyUser {
    return {
      companyId: this.selectedCompanyId,
      fullName: '',
      username: '',
      email: '',
      password: '',
      phone: '',
      role: 'OPERATOR',
    };
  }

  private emptyIntegrationSettings(companyId = this.selectedCompanyId): CompanyIntegrationSettings {
    return {
      companyId,
      orangeCredentialsConfigured: false,
      whatsappCredentialsConfigured: false,
      smtpPasswordConfigured: false,
      smtpStartTls: true,
    };
  }
}
