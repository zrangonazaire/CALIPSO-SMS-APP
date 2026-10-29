import { computed, DestroyRef, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../auth/auth';
import { Company } from '../models/api.models';
import { CompanyService } from './company';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly auth = inject(AuthService);
  private readonly companyService = inject(CompanyService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadedCompanies = signal<Company[]>([]);
  private requestGeneration = 0;

  readonly selectedCompanyId = signal(0);
  readonly loading = signal(false);
  readonly isPlatformAdmin = computed(
    () =>
      this.auth.currentUser()?.platformAdmin === true && this.auth.currentUser()?.role === 'ADMIN',
  );
  readonly canManageTeam = computed(
    () =>
      this.isPlatformAdmin() || ['ADMIN', 'MANAGER'].includes(this.auth.currentUser()?.role || ''),
  );
  readonly companies = computed(() => {
    const user = this.auth.currentUser();
    if (!user) return [];
    return this.isPlatformAdmin()
      ? this.loadedCompanies()
      : this.loadedCompanies().filter((company) => company.id === user.companyId);
  });
  readonly activeCompany = computed<Company | undefined>(
    () =>
      this.companies().find((company) => company.id === this.selectedCompanyId()) ||
      this.companies()[0],
  );
  private readonly identity = computed(() => {
    const user = this.auth.currentUser();
    return user ? `${user.id}/${user.companyId}/${user.role}/${user.platformAdmin === true}` : '';
  });

  constructor() {
    effect(() => {
      const identity = this.identity();
      untracked(() => {
        this.requestGeneration++;
        this.loadedCompanies.set([]);
        this.selectedCompanyId.set(0);
        this.loading.set(false);
        if (identity) this.refreshCompanies(this.auth.currentUser()?.companyId);
      });
    });
  }

  refreshCompanies(preferredId?: number): void {
    const identity = this.identity();
    if (!identity) return;
    const generation = ++this.requestGeneration;
    this.loading.set(true);
    this.companyService
      .findAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (companies) => {
          if (generation !== this.requestGeneration || identity !== this.identity()) return;
          this.loadedCompanies.set(companies);
          const available = this.companies();
          const allowed = (id: number | undefined) =>
            available.some((company) => company.id === id);
          const currentId = this.selectedCompanyId();
          const preferred = preferredId || this.auth.currentUser()?.companyId;
          this.selectedCompanyId.set(
            allowed(preferredId)
              ? preferredId!
              : allowed(currentId)
                ? currentId
                : allowed(preferred)
                  ? preferred!
                  : available[0]?.id || 0,
          );
          this.loading.set(false);
        },
        error: () => {
          if (generation !== this.requestGeneration || identity !== this.identity()) return;
          this.loadedCompanies.set([]);
          this.selectedCompanyId.set(0);
          this.loading.set(false);
        },
      });
  }

  selectCompany(companyId: number): void {
    const id = Number(companyId);
    if (!this.companies().some((company) => company.id === id)) return;
    if (!this.isPlatformAdmin() && id !== this.auth.currentUser()?.companyId) return;
    this.selectedCompanyId.set(id);
  }
}
