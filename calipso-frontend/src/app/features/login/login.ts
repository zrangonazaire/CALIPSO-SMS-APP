import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth';

@Component({
  selector: 'app-login',
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  form = { username: '', password: '' };
  setupMode = false;
  setupForm = {
    companyName: '',
    fullName: '',
    username: '',
    email: '',
    password: '',
  };
  loading = false;
  error = '';

  submit(): void {
    if (!this.form.username.trim() || !this.form.password) {
      this.error = 'Saisissez votre identifiant et votre mot de passe.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.auth.login(this.form).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: () => {
        this.error = 'Identifiants invalides ou compte suspendu.';
        this.loading = false;
      },
    });
  }

  setupAdmin(): void {
    if (!this.setupForm.companyName.trim() || !this.setupForm.fullName.trim() || !this.setupForm.username.trim() || !this.setupForm.email.trim() || !this.setupForm.password) {
      this.error = 'Renseignez tous les champs pour initialiser le compte administrateur.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.auth.setupAdmin(this.setupForm).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: () => {
        this.error = 'Initialisation impossible. Un administrateur existe peut-etre deja.';
        this.loading = false;
      },
    });
  }
}
