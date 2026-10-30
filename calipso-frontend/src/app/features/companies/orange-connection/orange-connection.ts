import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CompanyIntegrationService } from '../../../core/services/company-integration';
import {
  OrangeConnectionConfiguration,
  OrangeConnectionStatus,
} from '../../../core/models/api.models';

@Component({
  selector: 'app-orange-connection',
  imports: [DatePipe],
  templateUrl: './orange-connection.html',
  styleUrl: './orange-connection.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrangeConnection {
  readonly companyId = input.required<number>();
  readonly companyName = input('');
  readonly configured = input(false);
  readonly clientId = input('');
  readonly clientSecret = input('');
  private readonly api = inject(CompanyIntegrationService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  readonly configuration = signal<OrangeConnectionConfiguration | undefined>(undefined);
  readonly result = signal<OrangeConnectionStatus | undefined>(undefined);
  readonly testing = signal(false);
  readonly error = signal('');
  readonly configurationError = signal('');

  constructor() {
    effect((onCleanup) => {
      const id = this.companyId();
      this.configuration.set(undefined);
      this.configurationError.set('');
      if (!id) return;
      const subscription = this.api.orangeConfiguration(id).subscribe({
        next: (config) => this.configuration.set(config),
        error: () =>
          this.configurationError.set('Impossible de charger les URLs Orange du serveur.'),
      });
      onCleanup(() => subscription.unsubscribe());
    });
    effect(() => {
      this.companyId();
      this.clientId();
      this.clientSecret();
      this.configured();
      this.request?.unsubscribe();
      this.result.set(undefined);
      this.testing.set(false);
      this.error.set('');
    });
  }

  test(): void {
    if (!this.companyId() || this.testing()) return;
    const id = this.clientId().trim(),
      secret = this.clientSecret().trim();
    this.result.set(undefined);
    this.error.set('');
    if (!!id !== !!secret) {
      this.error.set(
        'Saisissez le Client ID et le Client Secret ensemble, ou laissez les deux champs vides pour tester les identifiants enregistrés.',
      );
      return;
    }
    if (!id && !this.configured()) {
      this.error.set('Renseignez les identifiants Orange de cette entreprise.');
      return;
    }
    this.testing.set(true);
    this.request = this.api
      .testOrange(this.companyId(), id || undefined, secret || undefined)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.testing.set(false);
        },
        error: (err) => {
          this.testing.set(false);
          this.error.set(err.error?.message || 'Connexion Orange impossible actuellement.');
        },
      });
  }
}
