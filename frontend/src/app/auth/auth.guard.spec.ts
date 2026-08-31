import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ApplicationRole } from './application-role';
import { authGuard, roleGuard } from './auth.guard';
import { AuthService } from './auth.service';

@Component({
  standalone: true,
  template: ''
})
class EmptyRouteComponent {}

describe('auth guards', () => {
  let authService: AuthService;
  let httpTesting: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: EmptyRouteComponent },
          { path: 'protected', component: EmptyRouteComponent, canActivate: [authGuard] },
          { path: 'admin', component: EmptyRouteComponent, canActivate: [roleGuard(ApplicationRole.ADMIN)] }
        ]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    TestBed.inject(HttpClient);
    authService = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('redirects anonymous users to the fixed safe route', async () => {
    const result = await router.navigateByUrl('/protected');

    expect(result).toBe(true);
    expect(router.url).toBe('/');
  });

  it('allows authenticated users through the auth guard', async () => {
    authenticate([ApplicationRole.USER]);

    const result = await router.navigateByUrl('/protected');

    expect(result).toBe(true);
    expect(router.url).toBe('/protected');
  });

  it('requires the configured ApplicationRole for the role guard', async () => {
    authenticate([ApplicationRole.USER]);

    const denied = await router.navigateByUrl('/admin');

    expect(denied).toBe(true);
    expect(router.url).toBe('/');

    authenticate([ApplicationRole.ADMIN]);
    const allowed = await router.navigateByUrl('/admin');

    expect(allowed).toBe(true);
    expect(router.url).toBe('/admin');
  });

  function authenticate(roles: readonly ApplicationRole[]): void {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/login').flush({
      accessToken: 'access-token',
      accessTokenExpiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
      user: {
        id: '8e31c835-756d-4609-a6d2-bf20d1505be3',
        email: 'user@example.com',
        roles
      }
    });
  }
});
