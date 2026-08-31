import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  if (!isProtectedApiRequest(request)) {
    return next(request);
  }

  return authService.refreshIfNeeded().pipe(
    switchMap((accessToken) => {
      const authorizedRequest = accessToken === null ? request : withBearerToken(request, accessToken);
      return next(authorizedRequest).pipe(
        catchError((error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 401 && accessToken !== null) {
            authService.clear();
          }
          return throwError(() => error);
        })
      );
    })
  );
};

function isProtectedApiRequest(request: HttpRequest<unknown>): boolean {
  return request.url.startsWith('/api/v1/') && !isAuthEndpoint(request.url);
}

function isAuthEndpoint(url: string): boolean {
  return [
    '/api/v1/auth/csrf',
    '/api/v1/auth/login',
    '/api/v1/auth/refresh',
    '/api/v1/auth/logout'
  ].includes(url);
}

function withBearerToken(request: HttpRequest<unknown>, accessToken: string): HttpRequest<unknown> {
  return request.clone({
    setHeaders: {
      Authorization: `Bearer ${accessToken}`
    }
  });
}
