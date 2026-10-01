import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';
import { routes } from './app.routes';
import { erroresInterceptor } from './core/auth/errores-interceptor';
import { SesionService } from './core/auth/sesion-service';

registerLocaleData(localeEsPe);
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([erroresInterceptor]),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
    ),
    provideAppInitializer(() =>
      firstValueFrom(
        inject(SesionService)
          .cargar()
          .pipe(catchError(() => of(null))),
      ),
    ),
    { provide: LOCALE_ID, useValue: 'es-PE' },
  ],
};
