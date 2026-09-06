import { HttpErrorResponse } from '@angular/common/http';
import { OverlayContainer } from '@angular/cdk/overlay';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

import { TurnstileApi, TurnstileRenderOptions } from '../core/security/turnstile';
import {
  DISCLOSURE_BROWSER_LOCATION,
  DISCLOSURE_DECLINE_REDIRECT_URL
} from './disclosure-acknowledgement';
import { FacialAnalysisApi, FacialAnalysisResult } from './facial-analysis-api';
import { LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE, LOOKALIKE_IMAGE_OVERSIZED_MESSAGE, LOOKALIKE_TURNSTILE_SITE_KEY } from './lookalike-config';
import { LookalikePage } from './lookalike-page';

class FacialAnalysisApiStub {
  response$: Observable<FacialAnalysisResult> = of(successResult);
  calls = 0;
  lastImage?: File;
  lastToken?: string;

  analyze(image: File, token: string): Observable<FacialAnalysisResult> {
    this.calls++;
    this.lastImage = image;
    this.lastToken = token;
    return this.response$;
  }
}

const successResult: FacialAnalysisResult = {
  successful: true,
  similarityPercentage: 31,
  level: 'LOW',
  phrase: 'A light Fedelobo resemblance showed up.'
};

let nextImageDimensions = { width: 1024, height: 768 };
let nextImageLoadFails = false;

describe('LookalikePage', () => {
  let fixture: ComponentFixture<LookalikePage>;
  let component: LookalikePage;
  let api: FacialAnalysisApiStub;
  let browserLocation: { assign: ReturnType<typeof vi.fn> };
  let overlayContainer: OverlayContainer;
  let createdUrls: string[];
  let revokedUrls: string[];
  let renderedOptions: TurnstileRenderOptions;
  let resetCalls: string[];

  beforeEach(async () => {
    api = new FacialAnalysisApiStub();
    browserLocation = { assign: vi.fn() };
    createdUrls = [];
    revokedUrls = [];
    resetCalls = [];
    nextImageDimensions = { width: 1024, height: 768 };
    nextImageLoadFails = false;
    sessionStorage.setItem('lookalike.disclosure.accepted', 'true');
    vi.spyOn(URL, 'createObjectURL').mockImplementation((file) => {
      const url = `blob:${(file as File).name}-${createdUrls.length}`;
      createdUrls.push(url);
      return url;
    });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((url) => revokedUrls.push(url));
    vi.stubGlobal('Image', MockImage);

    window.turnstile = {
      render: (_container: HTMLElement, options: TurnstileRenderOptions) => {
        renderedOptions = options;
        return 'widget-id';
      },
      remove: () => undefined,
      reset: (widgetId: string) => resetCalls.push(widgetId)
    } satisfies TurnstileApi;

    await TestBed.configureTestingModule({
      imports: [LookalikePage],
      providers: [
        { provide: FacialAnalysisApi, useValue: api },
        { provide: DISCLOSURE_BROWSER_LOCATION, useValue: browserLocation },
        { provide: LOOKALIKE_TURNSTILE_SITE_KEY, useValue: 'site-key' }
      ]
    }).compileComponents();

    overlayContainer = TestBed.inject(OverlayContainer);
    fixture = TestBed.createComponent(LookalikePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
    sessionStorage.clear();
    overlayContainer.ngOnDestroy();
    delete window.turnstile;
  });

  it('shows the disclosure before the analysis flow is usable', async () => {
    await recreateWithoutDisclosureAcceptance();

    expect(overlayText()).toContain('Aviso de entretenimiento, uso de imagen y persona de referencia');
    expect(overlayText()).toContain('Entiendo y continuar');
    expect(overlayText()).toContain('Salir');
    expect(fixture.nativeElement.querySelector('#photo-input')).toBeNull();
    expect(component.canSubmit()).toBe(false);
  });

  it('accepting the disclosure enables the normal flow for the session', async () => {
    await recreateWithoutDisclosureAcceptance();

    await closeDisclosure('Entiendo y continuar');

    expect(component.disclosureAccepted()).toBe(true);
    expect(sessionStorage.getItem('lookalike.disclosure.accepted')).toBe('true');
    expect(fixture.nativeElement.querySelector('#photo-input')).not.toBeNull();
    expect(browserLocation.assign).not.toHaveBeenCalled();
  });

  it('declining the disclosure does not acknowledge and redirects away', async () => {
    await recreateWithoutDisclosureAcceptance();

    await closeDisclosure('Salir');

    expect(component.disclosureAccepted()).toBe(false);
    expect(sessionStorage.getItem('lookalike.disclosure.accepted')).toBeNull();
    expect(fixture.nativeElement.querySelector('#photo-input')).toBeNull();
    expect(component.canSubmit()).toBe(false);
    expect(browserLocation.assign).toHaveBeenCalledWith(DISCLOSURE_DECLINE_REDIRECT_URL);
  });

  it('starts in the initial state', () => {
    expect(component.state()).toBe('idle');
    expect(component.canSubmit()).toBe(false);
    expect(text()).toContain('Select a JPEG photo to begin.');
  });

  it('renders persistent upload requirements before file selection', () => {
    expect(text()).toContain('JPEG · max 6 MB · up to 4096 × 4096 px · max 12 MP');
    expect(text()).toContain('For better results: one face per image.');
    expect((fixture.nativeElement.querySelector('#photo-input') as HTMLInputElement).getAttribute('aria-describedby')).toBe('photo-help');
  });

  it('creates a preview for a valid image selection', async () => {
    await selectFiles([jpegFile('face.jpg')]);

    expect(component.selectedFile()?.name).toBe('face.jpg');
    expect(component.previewUrl()).toBe('blob:face.jpg-0');
    expect(component.state()).toBe('previewReady');
    expect(text()).toContain('face.jpg');
  });

  it('shows a client error when submitting without a selected image', () => {
    component.handleTokenChange('token');

    component.submit();

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toContain('Choose one JPEG photo');
    expect(api.calls).toBe(0);
  });

  it('rejects multiple files before submission', async () => {
    await selectFiles([jpegFile('first.jpg'), jpegFile('second.jpg')]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toContain('Only one photo');
    expect(api.calls).toBe(0);
  });

  it('rejects empty files before submission', async () => {
    await selectFiles([jpegFile('empty.jpg', 0)]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toContain('empty');
    expect(api.calls).toBe(0);
  });

  it('rejects invalid file types before submission', async () => {
    await selectFiles([jpegFile('old.jpg')]);
    await selectFiles([new File(['data'], 'face.png', { type: 'image/png' })]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toContain('JPEG');
    expect(component.previewUrl()).toBeNull();
    expect(api.calls).toBe(0);
  });

  it('rejects oversized files before submission', async () => {
    await selectFiles([jpegFile('large.jpg', component.maxImageBytes + 1)]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toBe(LOOKALIKE_IMAGE_OVERSIZED_MESSAGE);
    expect(api.calls).toBe(0);
  });

  it('rejects images wider than the supported dimensions before submission', async () => {
    nextImageDimensions = { width: 4097, height: 1000 };

    await selectFiles([jpegFile('wide.jpg')]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toBe(LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE);
    expect(component.previewUrl()).toBeNull();
    expect(api.calls).toBe(0);
  });

  it('rejects images taller than the supported dimensions before submission', async () => {
    nextImageDimensions = { width: 1000, height: 4097 };

    await selectFiles([jpegFile('tall.jpg')]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toBe(LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE);
    expect(api.calls).toBe(0);
  });

  it('rejects images above the total pixel limit before submission', async () => {
    nextImageDimensions = { width: 4000, height: 4000 };

    await selectFiles([jpegFile('large-dimensions.jpg')]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toBe(LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE);
    expect(api.calls).toBe(0);
  });

  it('maps corrupt image metadata loading to a recoverable error before submission', async () => {
    nextImageLoadFails = true;

    await selectFiles([jpegFile('broken.jpg')]);

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toContain('could not be read');
    expect(api.calls).toBe(0);
  });

  it('revokes the previous preview when a new image replaces it and on destroy', async () => {
    await selectFiles([jpegFile('first.jpg')]);
    await selectFiles([jpegFile('second.jpg')]);

    expect(revokedUrls).toEqual(['blob:first.jpg-0']);

    fixture.destroy();

    expect(revokedUrls).toEqual(['blob:first.jpg-0', 'blob:second.jpg-1']);
  });

  it('uses Turnstile token changes to enable and disable readiness', async () => {
    await selectFiles([jpegFile('face.jpg')]);
    renderedOptions.callback?.('token');
    fixture.detectChanges();

    expect(component.canSubmit()).toBe(true);

    renderedOptions['expired-callback']?.();
    fixture.detectChanges();

    expect(component.canSubmit()).toBe(false);

    renderedOptions.callback?.('token-2');
    fixture.detectChanges();
    const handled = renderedOptions['error-callback']?.();
    fixture.detectChanges();

    expect(handled).toBe(true);
    expect(component.canSubmit()).toBe(false);
  });

  it('prevents duplicate submissions while loading', async () => {
    const pending = new Subject<FacialAnalysisResult>();
    api.response$ = pending.asObservable();
    await selectReadyImage();

    component.submit();
    component.submit();

    expect(api.calls).toBe(1);
    expect(component.state()).toBe('submitting');
    expect(text()).toContain('Analyzing');

    pending.next(successResult);
    pending.complete();
  });

  it('renders successful backend result values exactly as returned', async () => {
    api.response$ = of({
      successful: true,
      similarityPercentage: 73,
      level: 'BACKEND_LEVEL',
      phrase: 'Backend selected phrase.'
    });
    await selectReadyImage();

    component.submit();
    fixture.detectChanges();

    expect(text()).toContain('73%');
    expect(text()).toContain('BACKEND_LEVEL');
    expect(text()).toContain('Backend selected phrase.');
    expect(component.turnstileToken()).toBeNull();
    expect(resetCalls).toEqual(['widget-id']);
  });

  it('maps no-face and multiple-face backend errors', async () => {
    await selectReadyImage();
    api.response$ = backendError('FACE_NO_USABLE_FACE');
    component.submit();

    expect(component.errorMessage()).toContain('No usable face');

    renderedOptions.callback?.('token-2');
    api.response$ = backendError('FACE_MULTIPLE_USABLE_FACES');
    component.submit();

    expect(component.errorMessage()).toContain('Multiple faces');
  });

  it('maps invalid Turnstile backend errors', async () => {
    await selectReadyImage();
    api.response$ = backendError('SECURITY_TURNSTILE_INVALID');

    component.submit();

    expect(component.errorMessage()).toContain('verification');
    expect(component.turnstileToken()).toBeNull();
    expect(resetCalls).toEqual(['widget-id']);
  });

  it('starts and clears cooldown from retryAfterSeconds', async () => {
    await selectReadyImage();
    vi.useFakeTimers();
    api.response$ = backendError('SECURITY_COOLDOWN_ACTIVE', 3);

    component.submit();
    fixture.detectChanges();

    expect(component.state()).toBe('cooldown');
    expect(component.cooldownRemainingSeconds()).toBe(3);
    expect(component.canSubmit()).toBe(false);

    vi.advanceTimersByTime(3000);
    fixture.detectChanges();

    expect(component.cooldownRemainingSeconds()).toBe(0);
    expect(component.state()).toBe('previewReady');
  });

  it('maps network or unexpected failures to recoverable errors', async () => {
    await selectReadyImage();
    api.response$ = throwError(() => new HttpErrorResponse({ status: 0 }));

    component.submit();

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toContain('Network connection failed');
  });

  it('maps unexpected backend failures without raw internals', async () => {
    await selectReadyImage();
    api.response$ = throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'stack detail' } }));

    component.submit();

    expect(component.state()).toBe('recoverableError');
    expect(component.errorMessage()).toBe('The analysis failed. Try again.');
  });

  it('maps backend image size errors to clear frontend messages', async () => {
    await selectReadyImage();
    api.response$ = backendError('FACE_IMAGE_OVERSIZED');

    component.submit();

    expect(component.errorMessage()).toBe(LOOKALIKE_IMAGE_OVERSIZED_MESSAGE);
  });

  it('maps backend dimension errors to clear frontend messages', async () => {
    await selectReadyImage();
    api.response$ = backendError('FACE_IMAGE_DIMENSIONS_UNSUPPORTED');

    component.submit();

    expect(component.errorMessage()).toBe(LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE);
  });

  it('supports retry reset without clearing the selected image', async () => {
    await selectReadyImage();
    component.submit();

    component.resetAttempt();

    expect(component.result()).toBeNull();
    expect(component.previewUrl()).toBe('blob:face.jpg-0');
    expect(component.state()).toBe('previewReady');
  });

  async function selectReadyImage(): Promise<void> {
    await selectFiles([jpegFile('face.jpg')]);
    renderedOptions.callback?.('turnstile-token');
    fixture.detectChanges();
  }

  async function selectFiles(files: File[]): Promise<void> {
    const input = fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: fileList(files)
    });
    input.dispatchEvent(new Event('change'));
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
  }

  function text(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function overlayText(): string {
    return overlayContainer.getContainerElement().textContent ?? '';
  }

  function overlayButton(label: string): HTMLButtonElement {
    const buttons = Array.from(overlayContainer.getContainerElement().querySelectorAll('button'));
    const button = buttons.find((candidate) => candidate.textContent?.includes(label));
    if (!button) {
      throw new Error(`Could not find overlay button: ${label}`);
    }
    return button;
  }

  async function recreateWithoutDisclosureAcceptance(): Promise<void> {
    fixture.destroy();
    overlayContainer.ngOnDestroy();
    sessionStorage.clear();
    fixture = TestBed.createComponent(LookalikePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function closeDisclosure(label: string): Promise<void> {
    overlayButton(label).dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 250));
    fixture.detectChanges();
    await fixture.whenStable();
  }
});

class MockImage {
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  naturalWidth = 0;
  naturalHeight = 0;

  set src(_value: string) {
    this.naturalWidth = nextImageDimensions.width;
    this.naturalHeight = nextImageDimensions.height;
    queueMicrotask(() => {
      if (nextImageLoadFails) {
        this.onerror?.();
        return;
      }
      this.onload?.();
    });
  }
}

function jpegFile(name: string, size = 4): File {
  return new File(['x'.repeat(size)], name, { type: 'image/jpeg' });
}

function fileList(files: File[]): FileList {
  return Object.assign(files, {
    item: (index: number) => files[index] ?? null
  }) as FileList;
}

function backendError(code: string, retryAfterSeconds?: number): Observable<FacialAnalysisResult> {
  return throwError(() => new HttpErrorResponse({
    status: code === 'SECURITY_COOLDOWN_ACTIVE' ? 429 : 400,
    error: {
      code,
      retryAfterSeconds
    }
  }));
}
