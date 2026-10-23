import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { AuthService } from '../auth/auth';
import { Company } from '../models/api.models';
import { CompanyService } from './company';

@Injectable({
  providedIn: 'root',
})
export class WorkspaceService {
  private readonly auth = inject(AuthService);
  private readonly companyService = inject(CompanyService);

  readonly companies = signal<Company[]>([]);
  readonly selectedCompanyId = signal<number>(0);
  readonly loading = signal<boolean>(false);

  readonly activeCompany = computed<Company | undefined>(() => {
    const list = this.companies();
    const id = this.selectedCompanyId();
    return list.find((c) => c.id === id) || list[0];
  });

  readonly isAdmin = computed<boolean>(() => {
    return this.auth.currentUser()?.role === 'ADMIN';
  });

  constructor() {
    effect(() => {
      const user = this.auth.currentUser();
      if (user) {
        this.refreshCompanies(user.companyId);
      } else {
        this.companies.set([]);
        this.selectedCompanyId.set(0);
      }
    });
  }

  refreshCompanies(preferredId?: number): void {
    this.loading.set(true);
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies.set(companies);
        const currentSelected = this.selectedCompanyId();
        const userCompanyId = preferredId || this.auth.currentUser()?.companyId;

        if (currentSelected && companies.some((c) => c.id === currentSelected)) {
          this.selectedCompanyId.set(currentSelected);
        } else if (userCompanyId && companies.some((c) => c.id === userCompanyId)) {
          this.selectedCompanyId.set(userCompanyId);
        } else if (companies.length > 0 && companies[0].id) {
          this.selectedCompanyId.set(companies[0].id);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  selectCompany(companyId: number): void {
    this.selectedCompanyId.set(Number(companyId));
  }
}
