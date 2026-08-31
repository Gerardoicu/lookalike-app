import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { ApplicationRole } from './application-role';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  return authService.isAuthenticated() ? true : inject(Router).parseUrl('/');
};

export function roleGuard(role: ApplicationRole): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    return authService.isAuthenticated() && authService.hasRole(role) ? true : inject(Router).parseUrl('/');
  };
}
