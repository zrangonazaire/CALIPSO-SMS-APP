import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth';
import { authInterceptor } from './auth.interceptor';

describe('Authorization errors', () => {
  let http: HttpTestingController;
  const clearSession = vi.fn();
  const navigateByUrl = vi.fn();
  beforeEach(() => {
    clearSession.mockReset();
    navigateByUrl.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: 'tenant-token', clearSession } },
        { provide: Router, useValue: { navigateByUrl } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('keeps a valid session when an operation is forbidden', () => {
    TestBed.inject(HttpClient)
      .get('/api/v1/companies/2')
      .subscribe({ error: () => undefined });
    const request = http.expectOne('/api/v1/companies/2');
    expect(request.request.headers.get('Authorization')).toBe('Bearer tenant-token');
    request.flush({}, { status: 403, statusText: 'Forbidden' });
    expect(clearSession).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('clears an expired session on an authentication error', () => {
    TestBed.inject(HttpClient)
      .get('/api/v1/companies')
      .subscribe({ error: () => undefined });
    http.expectOne('/api/v1/companies').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(clearSession).toHaveBeenCalledOnce();
    expect(navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
