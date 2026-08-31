import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

const TURNSTILE_SCRIPT_ID = 'cloudflare-turnstile-script';
const TURNSTILE_SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

@Injectable({ providedIn: 'root' })
export class TurnstileScriptLoader {
  private readonly document = inject(DOCUMENT);
  private loading?: Promise<void>;

  load(): Promise<void> {
    if (window.turnstile) {
      return Promise.resolve();
    }
    if (this.loading) {
      return this.loading;
    }

    const existingScript = this.document.getElementById(TURNSTILE_SCRIPT_ID) as HTMLScriptElement | null;
    if (existingScript) {
      this.loading = new Promise((resolve, reject) => {
        existingScript.addEventListener('load', () => resolve(), { once: true });
        existingScript.addEventListener('error', () => reject(new Error('Unable to load Cloudflare Turnstile.')), { once: true });
      });
      return this.loading;
    }

    this.loading = new Promise((resolve, reject) => {
      const script = this.document.createElement('script');
      script.id = TURNSTILE_SCRIPT_ID;
      script.src = TURNSTILE_SCRIPT_URL;
      script.async = true;
      script.defer = true;
      script.addEventListener('load', () => resolve(), { once: true });
      script.addEventListener('error', () => reject(new Error('Unable to load Cloudflare Turnstile.')), { once: true });
      this.document.head.appendChild(script);
    });
    return this.loading;
  }
}
