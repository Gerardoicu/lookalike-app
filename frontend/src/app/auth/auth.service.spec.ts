import { HttpClient, provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApplicationRole } from './application-role';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let authService: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })),
        provideHttpClientTesting()
      ]
    });
    TestBed.inject(HttpClient);
    authService = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('stores access tokens only in memory after login', () => {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/login').flush(authResponse('access-token', futureIso(15)));

    expect(authService.accessToken()).toBe('access-token');
    expect(authService.isAuthenticated()).toBe(true);
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);
  });

  it('initializes anonymous state when refresh fails on page reload', () => {
    authService.initialize().subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.initialized()).toBe(true);
    expect(authService.isAuthenticated()).toBe(false);
    expect(authService.hasAuthenticatedSession()).toBe(false);
  });

  it('restores authenticated state when refresh succeeds on page reload', () => {
    authService.initialize().subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush(authResponse('restored-token', futureIso(15)));

    expect(authService.initialized()).toBe(true);
    expect(authService.accessToken()).toBe('restored-token');
    expect(authService.hasRole(ApplicationRole.USER)).toBe(true);
  });

  it('shares one in-flight refresh operation', () => {
    authService.initialize().subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush(authResponse('initial-token', pastIso()));

    authService.refreshIfNeeded().subscribe();
    authService.refreshIfNeeded().subscribe();

    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush(authResponse('shared-token', futureIso(15)));
    httpTesting.expectNone('/api/v1/auth/refresh');
    expect(authService.accessToken()).toBe('shared-token');
  });

  it('clears state after refresh failure', () => {
    authService.initialize().subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush(authResponse('initial-token', pastIso()));

    authService.refreshIfNeeded().subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.accessToken()).toBeNull();
    expect(authService.isAuthenticated()).toBe(false);
  });

  it('clears state even when logout request fails', () => {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/login').flush(authResponse('access-token', futureIso(15)));

    authService.logout().subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/logout').flush({}, { status: 500, statusText: 'Server Error' });

    expect(authService.accessToken()).toBeNull();
    expect(authService.isAuthenticated()).toBe(false);
  });
});

function authResponse(accessToken: string, accessTokenExpiresAt: string) {
  return {
    accessToken,
    accessTokenExpiresAt,
    user: {
      id: '8e31c835-756d-4609-a6d2-bf20d1505be3',
      email: 'user@example.com',
      roles: [ApplicationRole.USER]
    }
  };
}

function futureIso(minutes: number): string {
  return new Date(Date.now() + minutes * 60_000).toISOString();
}

function pastIso(): string {
  return new Date(Date.now() - 60_000).toISOString();
}
