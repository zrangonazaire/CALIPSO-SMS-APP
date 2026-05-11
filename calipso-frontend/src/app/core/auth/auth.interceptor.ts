import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token;
  const isAuthEndpoint = request.url.includes('/api/v1/auth/login') || request.url.includes('/api/v1/auth/setup');

  if (!token || isAuthEndpoint) {
    return next(request).pipe(
      catchError((error) => {
        if (request.url.includes('/api/v1/') && !isAuthEndpoint && (error.status === 401 || error.status === 403)) {
          auth.clearSession();
          router.navigateByUrl('/login');
        }

        return throwError(() => error);
      })
    );
  }

  const authenticatedRequest = request.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authenticatedRequest).pipe(
    catchError((error) => {
      if (request.url.includes('/api/v1/') && (error.status === 401 || error.status === 403)) {
        auth.clearSession();
        router.navigateByUrl('/login');
      }

      return throwError(() => error);
    })
  );
};
