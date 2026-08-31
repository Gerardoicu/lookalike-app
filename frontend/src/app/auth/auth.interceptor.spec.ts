import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApplicationRole } from './application-role';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let authService: AuthService;
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    authService = TestBed.inject(AuthService);
    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('adds the bearer token when it remains valid for more than thirty seconds', () => {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    const loginRequest = httpTesting.expectOne('/api/v1/auth/login');
    loginRequest.flush({
      accessToken: 'access-token',
      accessTokenExpiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
      user: {
        id: '8e31c835-756d-4609-a6d2-bf20d1505be3',
        email: 'user@example.com',
        roles: [ApplicationRole.USER]
      }
    });

    httpClient.get<{ ok: boolean }>('/api/v1/protected').subscribe((response) => {
      expect(response.ok).toBe(true);
    });
    const protectedRequest = httpTesting.expectOne('/api/v1/protected');

    expect(protectedRequest.request.headers.get('Authorization')).toBe('Bearer access-token');
    protectedRequest.flush({ ok: true });
  });

  it('refreshes once before sending two concurrent requests inside the renewal window', () => {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/login').flush(authResponse('old-token', new Date(Date.now() + 10_000).toISOString()));

    httpClient.get<{ ok: boolean }>('/api/v1/protected-a').subscribe();
    httpClient.get<{ ok: boolean }>('/api/v1/protected-b').subscribe();

    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush(authResponse('new-token', new Date(Date.now() + 15 * 60_000).toISOString()));
    expect(httpTesting.match('/api/v1/auth/refresh')).toHaveLength(0);

    const first = httpTesting.expectOne('/api/v1/protected-a');
    const second = httpTesting.expectOne('/api/v1/protected-b');
    expect(first.request.headers.get('Authorization')).toBe('Bearer new-token');
    expect(second.request.headers.get('Authorization')).toBe('Bearer new-token');
    first.flush({ ok: true });
    second.flush({ ok: true });
  });

  it('clears authentication when refresh fails before a protected request', () => {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/login').flush(authResponse('old-token', new Date(Date.now() + 10_000).toISOString()));

    httpClient.get('/api/v1/protected').subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.isAuthenticated()).toBe(false);
    httpTesting.expectNone('/api/v1/protected');
  });

  it('does not refresh repeatedly for anonymous requests', () => {
    authService.markInitializedAnonymous();

    httpClient.get('/api/v1/protected').subscribe();
    const request = httpTesting.expectOne('/api/v1/protected');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
    httpTesting.expectNone('/api/v1/auth/refresh');
  });

  it('excludes auth endpoints and external urls', () => {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/login').flush(authResponse('access-token', new Date(Date.now() + 15 * 60_000).toISOString()));

    httpClient.post('/api/v1/auth/refresh', {}).subscribe();
    httpClient.get('https://example.com/api/v1/protected').subscribe();

    const refreshRequest = httpTesting.expectOne('/api/v1/auth/refresh');
    const externalRequest = httpTesting.expectOne('https://example.com/api/v1/protected');
    expect(refreshRequest.request.headers.has('Authorization')).toBe(false);
    expect(externalRequest.request.headers.has('Authorization')).toBe(false);
    refreshRequest.flush({});
    externalRequest.flush({});
  });

  it('clears authentication after a backend 401 for a valid-looking token without retrying', () => {
    authService.login({ email: 'user@example.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/csrf').flush(null);
    httpTesting.expectOne('/api/v1/auth/login').flush(authResponse('access-token', new Date(Date.now() + 15 * 60_000).toISOString()));

    httpClient.get('/api/v1/protected').subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/v1/protected').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.isAuthenticated()).toBe(false);
    httpTesting.expectNone('/api/v1/auth/refresh');
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
