import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_BASE } from '../api';
import {
  AuthUser,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  LoginRequest,
  LoginResponse,
  ResetPasswordRequest,
  SetupAdminRequest,
} from './auth.models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly apiUrl = `${API_BASE}/auth`;
  private readonly tokenKey = 'calipso_auth_token';
  private readonly userKey = 'calipso_auth_user';

  readonly currentUser = signal<AuthUser | null>(this.readStoredUser());

  constructor(private http: HttpClient, private router: Router) {}

  get token(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  get isAuthenticated(): boolean {
    return !!this.token && !!this.currentUser();
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, credentials).pipe(
      tap((response) => this.persistSession(response))
    );
  }

  setupAdmin(request: SetupAdminRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/setup`, request).pipe(
      tap((response) => this.persistSession(response))
    );
  }

  register(request: SetupAdminRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/register`, request).pipe(
      tap((response) => this.persistSession(response))
    );
  }

  requestPasswordReset(request: ForgotPasswordRequest): Observable<ForgotPasswordResponse> {
    return this.http.post<ForgotPasswordResponse>(`${this.apiUrl}/forgot-password`, request);
  }

  confirmPasswordReset(request: ResetPasswordRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/reset-password`, request).pipe(
      tap((response) => this.persistSession(response))
    );
  }

  changePassword(request: ChangePasswordRequest): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/change-password`, request);
  }

  loadMe(): Observable<AuthUser> {
    const sessionToken = this.token;
    return this.http.get<AuthUser>(`${this.apiUrl}/me`).pipe(
      tap((user) => {
        if (sessionToken !== this.token || !sessionToken) return;
        this.currentUser.set(user);
        localStorage.setItem(this.userKey, JSON.stringify(user));
      })
    );
  }

  logout(): void {
    const token = this.token;
    if (token) {
      this.http.post<void>(`${this.apiUrl}/logout`, {}).subscribe({ error: () => undefined });
    }
    this.clearSession();
    this.router.navigateByUrl('/login');
  }

  clearSession(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
    this.currentUser.set(null);
  }

  private persistSession(response: LoginResponse): void {
    localStorage.setItem(this.tokenKey, response.token);
    localStorage.setItem(this.userKey, JSON.stringify(response.user));
    this.currentUser.set(response.user);
  }

  private readStoredUser(): AuthUser | null {
    const raw = localStorage.getItem(this.userKey);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      localStorage.removeItem(this.userKey);
      return null;
    }
  }
}
