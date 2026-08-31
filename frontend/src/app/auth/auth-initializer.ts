import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';

export function initializeAuth(): Promise<void> {
  return firstValueFrom(inject(AuthService).initialize());
}
