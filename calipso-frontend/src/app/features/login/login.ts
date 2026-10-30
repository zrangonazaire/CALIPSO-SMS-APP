import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { CampaignChannel } from '../../core/models/api.models';

@Component({
  selector: 'app-login',
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly publicRegistrationEnabled = signal(false);

  constructor() {
    this.auth.configuration().subscribe({
      next: settings => this.publicRegistrationEnabled.set(settings.publicRegistrationEnabled),
      error: () => this.publicRegistrationEnabled.set(false),
    });
  }

  mode: 'login' | 'register' | 'forgot' = 'login';
  forgotStep: 1 | 2 = 1;

  form = { username: '', password: '' };

  setupForm = {
    companyName: '',
    fullName: '',
    username: '',
    email: '',
    password: '',
  };

  forgotForm = {
    identifier: '',
    channel: 'EMAIL' as CampaignChannel,
    code: '',
    newPassword: '',
  };

  maskedDestination = '';
  devCodeHint = '';
  loading = false;
  error = '';
  info = '';

  switchMode(newMode: 'login' | 'register' | 'forgot'): void {
    if (newMode === 'register' && !this.publicRegistrationEnabled()) return;
    this.mode = newMode;
    this.forgotStep = 1;
    this.error = '';
    this.info = '';
  }

  submit(): void {
    if (!this.form.username.trim() || !this.form.password) {
      this.error = 'Saisissez votre identifiant (ou email) et votre mot de passe.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.auth.login(this.form).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: () => {
        this.error = 'Identifiants incorrects ou compte suspendu.';
        this.loading = false;
      },
    });
  }

  registerAccount(): void {
    if (!this.publicRegistrationEnabled()) return;
    if (
      !this.setupForm.companyName.trim() ||
      !this.setupForm.fullName.trim() ||
      !this.setupForm.username.trim() ||
      !this.setupForm.email.trim() ||
      !this.setupForm.password
    ) {
      this.error = 'Veuillez remplir tous les champs pour créer votre espace.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.auth.register(this.setupForm).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (err) => {
        this.error = err.error?.message || 'Impossible de créer le compte (identifiant peut-être déjà pris).';
        this.loading = false;
      },
    });
  }

  requestResetCode(): void {
    if (!this.forgotForm.identifier.trim()) {
      this.error = 'Saisissez votre identifiant ou votre adresse email.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.info = '';
    this.auth
      .requestPasswordReset({
        identifier: this.forgotForm.identifier.trim(),
        channel: this.forgotForm.channel,
      })
      .subscribe({
        next: (res) => {
          this.loading = false;
          this.forgotStep = 2;
          this.maskedDestination = res.maskedDestination;
          this.devCodeHint = res.devCodeHint || '';
          this.info = `${res.message} (${res.maskedDestination})`;
        },
        error: (err) => {
          this.loading = false;
          this.error = err.error?.message || 'Aucun compte actif trouvé pour cet identifiant ou email.';
        },
      });
  }

  confirmResetPassword(): void {
    if (!this.forgotForm.code.trim() || !this.forgotForm.newPassword) {
      this.error = 'Saisissez le code à 6 chiffres et votre nouveau mot de passe.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.auth
      .confirmPasswordReset({
        identifier: this.forgotForm.identifier.trim(),
        code: this.forgotForm.code.trim(),
        newPassword: this.forgotForm.newPassword,
      })
      .subscribe({
        next: () => this.router.navigateByUrl('/dashboard'),
        error: (err) => {
          this.loading = false;
          this.error = err.error?.message || 'Code de sécurité invalide ou expiré.';
        },
      });
  }
}
