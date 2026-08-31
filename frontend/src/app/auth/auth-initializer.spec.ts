import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';

import { initializeAuth } from './auth-initializer';
import { AuthService } from './auth.service';

describe('initializeAuth', () => {
  it('waits for AuthService initialization', async () => {
    let called = false;
    const authService: Pick<AuthService, 'initialize'> = {
      initialize: (): Observable<void> => {
        called = true;
        return of(undefined);
      }
    };
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authService }]
    });

    await TestBed.runInInjectionContext(() => initializeAuth());

    expect(called).toBe(true);
  });
});
