import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription, timer } from 'rxjs';

import { TurnstileWidget } from '../core/security/turnstile-widget';
import { ApiProblemDetail, FacialAnalysisApi, FacialAnalysisResult } from './facial-analysis-api';
import {
  LOOKALIKE_FACE_REQUIREMENT,
  LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE,
  LOOKALIKE_IMAGE_OVERSIZED_MESSAGE,
  LOOKALIKE_IMAGE_REQUIREMENTS,
  LOOKALIKE_MAX_IMAGE_BYTES,
  LOOKALIKE_MAX_IMAGE_HEIGHT,
  LOOKALIKE_MAX_IMAGE_PIXELS,
  LOOKALIKE_MAX_IMAGE_WIDTH,
  LOOKALIKE_TURNSTILE_ACTION,
  LOOKALIKE_TURNSTILE_SITE_KEY
} from './lookalike-config';

type ExperienceState = 'idle' | 'previewReady' | 'submitting' | 'success' | 'cooldown' | 'recoverableError';
interface ImageDimensions {
  width: number;
  height: number;
}

const ERROR_MESSAGES: Record<string, string> = {
  FACE_IMAGE_MISSING: 'Choose one JPEG photo before starting the analysis.',
  FACE_IMAGE_MULTIPLE_FILES: 'Only one photo can be analyzed at a time.',
  FACE_IMAGE_EMPTY: 'The selected photo is empty. Choose another JPEG.',
  FACE_IMAGE_OVERSIZED: LOOKALIKE_IMAGE_OVERSIZED_MESSAGE,
  FACE_IMAGE_UNSUPPORTED_FORMAT: 'Choose a JPEG photo for this version.',
  FACE_IMAGE_CORRUPT: 'That photo could not be read. Choose another JPEG.',
  FACE_IMAGE_DIMENSIONS_UNSUPPORTED: LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE,
  FACE_NO_USABLE_FACE: 'No usable face was detected. Try a clear front-facing photo.',
  FACE_MULTIPLE_USABLE_FACES: 'Multiple faces were detected. Choose a photo with one person.',
  FACE_ANALYSIS_UNAVAILABLE: 'The facial analysis service is temporarily unavailable.',
  FEDELOBO_PROFILE_UNAVAILABLE: 'The Fedelobo profile is temporarily unavailable.',
  SECURITY_TURNSTILE_INVALID: 'The verification could not be confirmed. Complete it again.',
  SECURITY_TURNSTILE_MISSING: 'Complete the verification before analyzing.',
  SECURITY_TURNSTILE_OVERSIZED: 'The verification response was invalid. Complete it again.',
  SECURITY_TURNSTILE_REUSED_OR_EXPIRED: 'The verification expired. Complete it again.',
  SECURITY_TURNSTILE_UNEXPECTED_ACTION: 'The verification response did not match this action.',
  SECURITY_TURNSTILE_UNEXPECTED_HOSTNAME: 'The verification response did not match this site.',
  SECURITY_TURNSTILE_UNAVAILABLE: 'Verification is temporarily unavailable.',
  SECURITY_RATE_LIMITED: 'Too many attempts were made. Wait a moment before trying again.',
  SECURITY_CONFIGURATION_UNAVAILABLE: 'Analysis is temporarily unavailable.',
  SECURITY_COOLDOWN_ACTIVE: 'Your next analysis is not ready yet.'
};

@Component({
  selector: 'app-lookalike-page',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, NgClass, TurnstileWidget],
  templateUrl: './lookalike-page.html',
  styleUrl: './lookalike-page.scss'
})
export class LookalikePage implements OnDestroy {
  private readonly api = inject(FacialAnalysisApi);
  readonly turnstileSiteKey = inject(LOOKALIKE_TURNSTILE_SITE_KEY);
  readonly turnstileAction = LOOKALIKE_TURNSTILE_ACTION;
  readonly maxImageBytes = LOOKALIKE_MAX_IMAGE_BYTES;
  readonly imageRequirements = LOOKALIKE_IMAGE_REQUIREMENTS;
  readonly faceRequirement = LOOKALIKE_FACE_REQUIREMENT;

  readonly selectedFile = signal<File | null>(null);
  readonly previewUrl = signal<string | null>(null);
  readonly turnstileToken = signal<string | null>(null);
  readonly state = signal<ExperienceState>('idle');
  readonly errorMessage = signal<string | null>(null);
  readonly result = signal<FacialAnalysisResult | null>(null);
  readonly cooldownRemainingSeconds = signal(0);

  readonly canSubmit = computed(() =>
    this.selectedFile() !== null &&
    this.previewUrl() !== null &&
    this.turnstileToken() !== null &&
    this.state() !== 'submitting' &&
    this.cooldownRemainingSeconds() === 0
  );

  readonly statusMessage = computed(() => {
    if (this.state() === 'submitting') {
      return 'Analyzing your photo.';
    }
    if (this.cooldownRemainingSeconds() > 0) {
      return `Try again in ${this.cooldownRemainingSeconds()} seconds.`;
    }
    return this.errorMessage() ?? '';
  });

  @ViewChild('fileInput') private readonly fileInput?: ElementRef<HTMLInputElement>;
  @ViewChild(TurnstileWidget) private readonly turnstileWidget?: TurnstileWidget;

  private cooldownSubscription?: Subscription;
  private selectionVersion = 0;

  ngOnDestroy(): void {
    this.revokePreview();
    this.cooldownSubscription?.unsubscribe();
  }

  selectFromInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    void this.selectFiles(input.files);
  }

  selectFromDrop(event: DragEvent): void {
    event.preventDefault();
    void this.selectFiles(event.dataTransfer?.files ?? null);
  }

  allowDrop(event: DragEvent): void {
    event.preventDefault();
  }

  clearSelection(): void {
    this.selectedFile.set(null);
    this.result.set(null);
    this.errorMessage.set(null);
    this.revokePreview();
    this.state.set(this.cooldownRemainingSeconds() > 0 ? 'cooldown' : 'idle');
    if (this.fileInput?.nativeElement) {
      this.fileInput.nativeElement.value = '';
    }
  }

  handleTokenChange(token: string | null): void {
    this.turnstileToken.set(token);
    if (token === null && this.state() === 'previewReady' && this.errorMessage() === null) {
      this.errorMessage.set('Complete the verification to continue.');
    }
  }

  submit(): void {
    if (this.state() === 'submitting') {
      return;
    }
    const file = this.selectedFile();
    const token = this.turnstileToken();
    if (!file) {
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_MISSING']);
      return;
    }
    if (!token) {
      this.showClientError(ERROR_MESSAGES['SECURITY_TURNSTILE_MISSING']);
      return;
    }
    if (this.cooldownRemainingSeconds() > 0) {
      this.state.set('cooldown');
      return;
    }

    this.state.set('submitting');
    this.errorMessage.set(null);
    this.result.set(null);

    this.api.analyze(file, token).subscribe({
      next: (result) => {
        this.result.set(result);
        this.state.set('success');
        this.resetTurnstileAfterSubmittedRequest();
      },
      error: (error: unknown) => {
        this.handleSubmissionError(error);
        this.resetTurnstileAfterSubmittedRequest();
      }
    });
  }

  resetAttempt(): void {
    this.result.set(null);
    this.errorMessage.set(null);
    this.state.set(this.previewUrl() ? 'previewReady' : 'idle');
  }

  private async selectFiles(files: FileList | null): Promise<void> {
    const version = ++this.selectionVersion;
    this.result.set(null);
    this.errorMessage.set(null);
    if (!files || files.length === 0) {
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_MISSING']);
      return;
    }
    this.selectedFile.set(null);
    this.revokePreview();
    if (files.length !== 1) {
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_MULTIPLE_FILES']);
      return;
    }

    const file = files.item(0);
    if (!file) {
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_MISSING']);
      return;
    }
    if (!this.isJpeg(file)) {
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_UNSUPPORTED_FORMAT']);
      return;
    }
    if (file.size === 0) {
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_EMPTY']);
      return;
    }
    if (file.size > this.maxImageBytes) {
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_OVERSIZED']);
      return;
    }

    const previewUrl = URL.createObjectURL(file);
    try {
      const dimensions = await this.readImageDimensions(previewUrl);
      if (version !== this.selectionVersion) {
        URL.revokeObjectURL(previewUrl);
        return;
      }
      if (!this.isSupportedDimensions(dimensions)) {
        URL.revokeObjectURL(previewUrl);
        this.showClientError(ERROR_MESSAGES['FACE_IMAGE_DIMENSIONS_UNSUPPORTED']);
        return;
      }
    }
    catch {
      URL.revokeObjectURL(previewUrl);
      this.showClientError(ERROR_MESSAGES['FACE_IMAGE_CORRUPT']);
      return;
    }

    this.selectedFile.set(file);
    this.previewUrl.set(previewUrl);
    this.state.set(this.cooldownRemainingSeconds() > 0 ? 'cooldown' : 'previewReady');
  }

  private isJpeg(file: File): boolean {
    return file.type === 'image/jpeg' || /\.(jpg|jpeg)$/i.test(file.name);
  }

  private readImageDimensions(objectUrl: string): Promise<ImageDimensions> {
    return new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight });
      image.onerror = () => reject(new Error('Unable to read image dimensions.'));
      image.src = objectUrl;
    });
  }

  private isSupportedDimensions(dimensions: ImageDimensions): boolean {
    return dimensions.width > 0 &&
      dimensions.height > 0 &&
      dimensions.width <= LOOKALIKE_MAX_IMAGE_WIDTH &&
      dimensions.height <= LOOKALIKE_MAX_IMAGE_HEIGHT &&
      dimensions.width * dimensions.height <= LOOKALIKE_MAX_IMAGE_PIXELS;
  }

  private handleSubmissionError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        this.errorMessage.set('Network connection failed. Check the backend and try again.');
        this.state.set('recoverableError');
        return;
      }
      const problem = this.problemDetail(error.error);
      if (problem?.code === 'SECURITY_COOLDOWN_ACTIVE') {
        this.startCooldown(problem.retryAfterSeconds);
        this.errorMessage.set(ERROR_MESSAGES['SECURITY_COOLDOWN_ACTIVE']);
        return;
      }
      this.errorMessage.set(problem?.code ? ERROR_MESSAGES[problem.code] ?? 'The analysis failed. Try again.' : 'The analysis failed. Try again.');
      this.state.set('recoverableError');
      return;
    }

    this.errorMessage.set('Network connection failed. Check the backend and try again.');
    this.state.set('recoverableError');
  }

  private problemDetail(value: unknown): ApiProblemDetail | null {
    if (typeof value !== 'object' || value === null) {
      return null;
    }
    const record = value as Record<string, unknown>;
    return {
      code: typeof record['code'] === 'string' ? record['code'] : undefined,
      retryAfterSeconds: typeof record['retryAfterSeconds'] === 'number' ? record['retryAfterSeconds'] : undefined
    };
  }

  private showClientError(message: string): void {
    this.result.set(null);
    this.errorMessage.set(message);
    this.state.set('recoverableError');
  }

  private startCooldown(retryAfterSeconds: number | undefined): void {
    const seconds = Math.max(1, Math.floor(retryAfterSeconds ?? 1));
    this.cooldownSubscription?.unsubscribe();
    this.cooldownRemainingSeconds.set(seconds);
    this.state.set('cooldown');
    this.cooldownSubscription = timer(1000, 1000).subscribe(() => {
      const next = Math.max(0, this.cooldownRemainingSeconds() - 1);
      this.cooldownRemainingSeconds.set(next);
      if (next === 0) {
        this.cooldownSubscription?.unsubscribe();
        this.state.set(this.previewUrl() ? 'previewReady' : 'idle');
        this.errorMessage.set(null);
      }
    });
  }

  private resetTurnstileAfterSubmittedRequest(): void {
    this.turnstileToken.set(null);
    this.turnstileWidget?.reset();
  }

  private revokePreview(): void {
    const currentPreview = this.previewUrl();
    if (currentPreview) {
      URL.revokeObjectURL(currentPreview);
      this.previewUrl.set(null);
    }
  }
}
