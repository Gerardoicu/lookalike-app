import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, map, Observable, of, shareReplay, switchMap, tap, throwError } from 'rxjs';

import { ApplicationRole } from './application-role';
import { AuthResponse, AuthenticatedUser, LoginRequest } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly accessTokenState = signal<string | null>(null);
  private readonly accessTokenExpiresAtState = signal<Date | null>(null);
  private readonly currentUserState = signal<AuthenticatedUser | null>(null);
  private readonly initializedState = signal(false);
  private readonly authenticatedSessionEstablishedState = signal(false);
  private refreshRequest: Observable<AuthResponse> | null = null;

  readonly user = this.currentUserState.asReadonly();
  readonly initialized = this.initializedState.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserState() !== null);

  accessToken(): string | null {
    return this.accessTokenState();
  }

  accessTokenExpiresAt(): Date | null {
    return this.accessTokenExpiresAtState();
  }

  hasAuthenticatedSession(): boolean {
    return this.authenticatedSessionEstablishedState();
  }

  hasRole(role: ApplicationRole): boolean {
    return this.currentUserState()?.roles.includes(role) ?? false;
  }

  initialize(): Observable<void> {
    return this.csrf().pipe(
      catchError(() => of(undefined)),
      switchMap(() => this.refreshWithCurrentCsrf().pipe(
        map(() => undefined),
        catchError(() => {
          this.markInitializedAnonymous();
          return of(undefined);
        })
      ))
    );
  }

  csrf(): Observable<void> {
    return this.http.get<void>('/api/v1/auth/csrf');
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthResponse>('/api/v1/auth/login', request)),
      tap((response) => {
        this.store(response);
      })
    );
  }

  refresh(): Observable<AuthResponse> {
    return this.sharedRefresh(() => this.csrf().pipe(
      switchMap(() => this.http.post<AuthResponse>('/api/v1/auth/refresh', {}))
    ));
  }

  private refreshWithCurrentCsrf(): Observable<AuthResponse> {
    return this.sharedRefresh(() => this.http.post<AuthResponse>('/api/v1/auth/refresh', {}));
  }

  private sharedRefresh(request: () => Observable<AuthResponse>): Observable<AuthResponse> {
    if (this.refreshRequest !== null) {
      return this.refreshRequest;
    }

    this.refreshRequest = request().pipe(
      tap((response) => {
        this.store(response);
      }),
      catchError((error: unknown) => {
        this.clear();
        return throwError(() => error);
      }),
      finalize(() => {
        this.refreshRequest = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );

    return this.refreshRequest;
  }

  refreshIfNeeded(now = new Date()): Observable<string | null> {
    const token = this.accessTokenState();
    const expiresAt = this.accessTokenExpiresAtState();
    if (token !== null && expiresAt !== null && expiresAt.getTime() - now.getTime() > 30_000) {
      return of(token);
    }
    if (!this.authenticatedSessionEstablishedState()) {
      return of(null);
    }
    return this.refresh().pipe(
      map((response) => response.accessToken),
      catchError((error: unknown) => throwError(() => error))
    );
  }

  logout(): Observable<void> {
    this.clear();
    return this.csrf().pipe(
      switchMap(() => this.http.post<void>('/api/v1/auth/logout', {})),
      catchError(() => of(undefined))
    );
  }

  clear(): void {
    this.accessTokenState.set(null);
    this.accessTokenExpiresAtState.set(null);
    this.currentUserState.set(null);
    this.authenticatedSessionEstablishedState.set(false);
  }

  markInitializedAnonymous(): void {
    this.initializedState.set(true);
  }

  private store(response: AuthResponse): void {
    this.accessTokenState.set(response.accessToken);
    this.accessTokenExpiresAtState.set(new Date(response.accessTokenExpiresAt));
    this.currentUserState.set(response.user);
    this.authenticatedSessionEstablishedState.set(true);
    this.initializedState.set(true);
  }
}
