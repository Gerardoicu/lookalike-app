import { InjectionToken } from '@angular/core';

export const LOOKALIKE_TURNSTILE_SITE_KEY = new InjectionToken<string>('Lookalike Turnstile site key', {
  providedIn: 'root',
  factory: () => '1x00000000000000000000AA'
});

export const LOOKALIKE_TURNSTILE_ACTION = 'analysis';
export const LOOKALIKE_MAX_IMAGE_BYTES = 6_291_456;
export const LOOKALIKE_MAX_IMAGE_MEGABYTES = LOOKALIKE_MAX_IMAGE_BYTES / 1024 / 1024;
export const LOOKALIKE_MAX_IMAGE_WIDTH = 4096;
export const LOOKALIKE_MAX_IMAGE_HEIGHT = 4096;
export const LOOKALIKE_MAX_IMAGE_PIXELS = 12_000_000;
export const LOOKALIKE_MAX_IMAGE_MEGAPIXELS = LOOKALIKE_MAX_IMAGE_PIXELS / 1_000_000;
export const LOOKALIKE_IMAGE_REQUIREMENTS =
  `JPEG · max ${LOOKALIKE_MAX_IMAGE_MEGABYTES} MB · up to ${LOOKALIKE_MAX_IMAGE_WIDTH} × ${LOOKALIKE_MAX_IMAGE_HEIGHT} px · max ${LOOKALIKE_MAX_IMAGE_MEGAPIXELS} MP`;
export const LOOKALIKE_FACE_REQUIREMENT = 'For better results: one face per image.';
export const LOOKALIKE_IMAGE_OVERSIZED_MESSAGE =
  `The image is too large. Maximum file size is ${LOOKALIKE_MAX_IMAGE_MEGABYTES} MB.`;
export const LOOKALIKE_IMAGE_DIMENSIONS_UNSUPPORTED_MESSAGE =
  `The image dimensions are too large. Use an image up to ${LOOKALIKE_MAX_IMAGE_WIDTH} × ${LOOKALIKE_MAX_IMAGE_HEIGHT} and ${LOOKALIKE_MAX_IMAGE_MEGAPIXELS} megapixels.`;
