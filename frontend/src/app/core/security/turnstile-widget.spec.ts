import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TurnstileApi, TurnstileRenderOptions } from './turnstile';
import { TurnstileWidget } from './turnstile-widget';

@Component({
  imports: [TurnstileWidget],
  template: '<app-turnstile-widget siteKey="site-key" action="analysis" (tokenChange)="token = $event" />'
})
class TurnstileHost {
  token: string | null = null;
}

describe('TurnstileWidget', () => {
  let fixture: ComponentFixture<TurnstileHost>;
  let renderedOptions: TurnstileRenderOptions;
  let removeCalls: string[];

  beforeEach(async () => {
    removeCalls = [];
    window.turnstile = {
      render: (_container: HTMLElement, options: TurnstileRenderOptions) => {
        renderedOptions = options;
        return 'widget-id';
      },
      remove: (widgetId: string) => removeCalls.push(widgetId),
      reset: () => undefined
    } satisfies TurnstileApi;

    await TestBed.configureTestingModule({
      imports: [TurnstileHost]
    }).compileComponents();

    fixture = TestBed.createComponent(TurnstileHost);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    delete window.turnstile;
  });

  it('renders explicit Turnstile widget with configured site key and action', () => {
    expect(renderedOptions.sitekey).toBe('site-key');
    expect(renderedOptions.action).toBe('analysis');
  });

  it('emits generated token and clears it on expiry or error', () => {
    renderedOptions.callback?.('turnstile-token');
    expect(fixture.componentInstance.token).toBe('turnstile-token');

    renderedOptions['expired-callback']?.();
    expect(fixture.componentInstance.token).toBeNull();

    const handled = renderedOptions['error-callback']?.();
    expect(handled).toBe(true);
    expect(fixture.componentInstance.token).toBeNull();
  });

  it('removes widget on destroy', () => {
    fixture.destroy();

    expect(removeCalls).toEqual(['widget-id']);
  });
});
