import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface FacialAnalysisResult {
  successful: true;
  similarityPercentage: number;
  level: string;
  phrase: string;
}

export interface ApiProblemDetail {
  code?: string;
  retryAfterSeconds?: number;
}

@Injectable({ providedIn: 'root' })
export class FacialAnalysisApi {
  private readonly http = inject(HttpClient);

  analyze(image: File, turnstileToken: string): Observable<FacialAnalysisResult> {
    const body = new FormData();
    body.append('image', image, image.name);

    return this.http.post<FacialAnalysisResult>('/api/v1/facial-analyses', body, {
      headers: new HttpHeaders({
        'X-Turnstile-Token': turnstileToken
      }),
      withCredentials: true
    });
  }
}
