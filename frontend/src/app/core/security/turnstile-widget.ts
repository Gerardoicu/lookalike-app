import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';

import { TurnstileScriptLoader } from './turnstile-script-loader';

@Component({
  selector: 'app-turnstile-widget',
  template: '<div #container class="turnstile-widget"></div>',
  styles: [
    `
      .turnstile-widget {
        min-height: 65px;
      }
    `
  ]
})
export class TurnstileWidget implements AfterViewInit, OnDestroy {
  private readonly scriptLoader = inject(TurnstileScriptLoader);
  private widgetId?: string;

  @Input({ required: true }) siteKey = '';
  @Input() action?: string;
  @Output() tokenChange = new EventEmitter<string | null>();
  @ViewChild('container', { static: true }) private readonly container!: ElementRef<HTMLElement>;

  async ngAfterViewInit(): Promise<void> {
    await this.scriptLoader.load();
    if (!window.turnstile) {
      this.tokenChange.emit(null);
      return;
    }
    this.widgetId = window.turnstile.render(this.container.nativeElement, {
      sitekey: this.siteKey,
      action: this.action,
      callback: (token: string) => this.tokenChange.emit(token),
      'expired-callback': () => this.tokenChange.emit(null),
      'error-callback': () => {
        this.tokenChange.emit(null);
        return true;
      }
    });
  }

  ngOnDestroy(): void {
    if (this.widgetId && window.turnstile) {
      window.turnstile.remove(this.widgetId);
    }
  }
}
