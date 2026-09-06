import { Injectable, InjectionToken, inject } from '@angular/core';

const DISCLOSURE_ACKNOWLEDGED_KEY = 'lookalike.disclosure.accepted';
export const DISCLOSURE_DECLINE_REDIRECT_URL = 'https://www.google.com/';
export const DISCLOSURE_BROWSER_LOCATION = new InjectionToken<Location>('Disclosure browser location', {
  providedIn: 'root',
  factory: () => window.location
});

@Injectable({ providedIn: 'root' })
export class DisclosureAcknowledgement {
  private memoryAccepted = false;

  isAccepted(): boolean {
    try {
      return sessionStorage.getItem(DISCLOSURE_ACKNOWLEDGED_KEY) === 'true';
    }
    catch {
      return this.memoryAccepted;
    }
  }

  accept(): void {
    this.memoryAccepted = true;
    try {
      sessionStorage.setItem(DISCLOSURE_ACKNOWLEDGED_KEY, 'true');
    }
    catch {
      // Session storage can be unavailable in restricted browser contexts.
    }
  }

  decline(): void {
    this.memoryAccepted = false;
    try {
      sessionStorage.removeItem(DISCLOSURE_ACKNOWLEDGED_KEY);
    }
    catch {
      // Session storage can be unavailable in restricted browser contexts.
    }
  }
}

@Injectable({ providedIn: 'root' })
export class DisclosureExitNavigation {
  private readonly location = inject(DISCLOSURE_BROWSER_LOCATION);

  redirect(): void {
    this.location.assign(DISCLOSURE_DECLINE_REDIRECT_URL);
  }
}
