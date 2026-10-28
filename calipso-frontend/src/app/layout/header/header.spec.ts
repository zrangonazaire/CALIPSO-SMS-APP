import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Header } from './header';
import { AuthService } from '../../core/auth/auth';
import { API_BASE } from '../../core/api';

describe('Header company selector', () => {
  const companyList = [
    { id: 1, name: 'MAGISER' },
    { id: 2, name: 'Autre entreprise' },
  ];
  for (const platformAdmin of [false, true]) {
    it(
      platformAdmin
        ? 'shows other companies only to an explicitly authorized platform administrator'
        : 'never shows another company to a MAGISER administrator',
      async () => {
        TestBed.configureTestingModule({
          imports: [Header],
          providers: [
            provideRouter([]),
            provideHttpClient(),
            provideHttpClientTesting(),
            {
              provide: AuthService,
              useValue: {
                currentUser: signal({
                  id: 10,
                  companyId: 1,
                  companyName: 'MAGISER',
                  fullName: 'Zrango',
                  role: 'ADMIN',
                  platformAdmin,
                }),
                logout: vi.fn(),
              },
            },
          ],
        });
        const fixture = TestBed.createComponent(Header);
        fixture.detectChanges();
        const http = TestBed.inject(HttpTestingController);
        http.expectOne(`${API_BASE}/companies`).flush(companyList);
        await fixture.whenStable();
        fixture.detectChanges();
        const selector: HTMLSelectElement | null =
          fixture.nativeElement.querySelector('.workspace-select');
        if (platformAdmin) {
          expect(selector).not.toBeNull();
          expect(selector?.options.length).toBe(2);
        } else {
          expect(selector).toBeNull();
          expect(fixture.nativeElement.textContent).not.toContain('Autre entreprise');
        }
        http.verify();
      },
    );
  }
});
