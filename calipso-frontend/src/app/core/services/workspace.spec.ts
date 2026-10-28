import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../auth/auth';
import { AuthUser } from '../auth/auth.models';
import { API_BASE } from '../api';
import { WorkspaceService } from './workspace';

describe('Workspace company isolation', () => {
  const user: AuthUser = {
    id: 10,
    companyId: 1,
    companyName: 'MAGISER',
    fullName: 'Zrango',
    username: 'zrango',
    email: 'zrango@example.ci',
    role: 'ADMIN',
    platformAdmin: false,
  };
  let identity: ReturnType<typeof signal<AuthUser | null>>;
  let http: HttpTestingController;
  let workspace: WorkspaceService;
  const companies = [
    { id: 1, name: 'MAGISER' },
    { id: 2, name: 'Autre entreprise' },
  ];

  beforeEach(() => {
    identity = signal<AuthUser | null>({ ...user });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { currentUser: identity } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    workspace = TestBed.inject(WorkspaceService);
    TestBed.tick();
  });
  afterEach(() => http.verify());

  it('restricts an ADMIN account to its company even if a response contains other companies', () => {
    http.expectOne(`${API_BASE}/companies`).flush(companies);
    expect(workspace.companies()).toEqual([companies[0]]);
    expect(workspace.isPlatformAdmin()).toBe(false);
    workspace.selectCompany(2);
    expect(workspace.selectedCompanyId()).toBe(1);
  });

  it('permits switching only for an explicitly authorized platform administrator', () => {
    http.expectOne(`${API_BASE}/companies`).flush(companies);
    identity.set({ ...user, platformAdmin: true });
    TestBed.tick();
    http.expectOne(`${API_BASE}/companies`).flush(companies);
    expect(workspace.companies()).toHaveLength(2);
    workspace.selectCompany(2);
    expect(workspace.activeCompany()?.id).toBe(2);
    workspace.selectCompany(999);
    expect(workspace.selectedCompanyId()).toBe(2);
  });

  it('discards a late company response belonging to the previous session', () => {
    const previous = http.expectOne(`${API_BASE}/companies`);
    identity.set({ ...user, id: 20, companyId: 2, companyName: 'Autre entreprise' });
    TestBed.tick();
    const current = http.expectOne(`${API_BASE}/companies`);
    previous.flush([companies[0]]);
    expect(workspace.companies()).toEqual([]);
    current.flush(companies);
    expect(workspace.companies()).toEqual([companies[1]]);
    expect(workspace.activeCompany()?.id).toBe(2);
    identity.set(null);
    expect(workspace.companies()).toEqual([]);
  });
});
