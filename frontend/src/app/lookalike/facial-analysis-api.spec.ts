import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { FacialAnalysisApi } from './facial-analysis-api';

describe('FacialAnalysisApi', () => {
  let api: FacialAnalysisApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(FacialAnalysisApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('submits one image to the relative facial analysis endpoint with credentials and Turnstile token', () => {
    const image = new File(['image'], 'face.jpg', { type: 'image/jpeg' });
    api.analyze(image, 'token').subscribe((result) => {
      expect(result.similarityPercentage).toBe(31);
    });

    const request = http.expectOne('/api/v1/facial-analyses');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-Turnstile-Token')).toBe('token');
    expect(request.request.body instanceof FormData).toBe(true);
    const submittedImage = (request.request.body as FormData).get('image') as File;
    expect(submittedImage.name).toBe('face.jpg');
    expect(submittedImage.type).toBe('image/jpeg');

    request.flush({
      successful: true,
      similarityPercentage: 31,
      level: 'LOW',
      phrase: 'A light Fedelobo resemblance showed up.'
    });
  });
});
