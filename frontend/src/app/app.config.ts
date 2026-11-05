import { provideServiceWorker } from '@angular/service-worker';
import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MAT_SNACK_BAR_DEFAULT_OPTIONS } from '@angular/material/snack-bar';
import { provideRouter, RouteReuseStrategy } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';
import { routes } from './app.routes';
import { erroresInterceptor } from './core/auth/errores-interceptor';
import { SesionService } from './core/auth/sesion-service';
import { negocioInterceptor } from './core/negocio/negocio-interceptor';
import { ReutilizacionNegocio } from './core/negocio/reutilizacion-negocio';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
    provideRouter(routes),
    { provide: RouteReuseStrategy, useClass: ReutilizacionNegocio },
    provideHttpClient(
      withInterceptors([negocioInterceptor, erroresInterceptor]),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
    ),
    provideAppInitializer(async () => {
      const sesion = inject(SesionService);
      await Promise.all([
        import('@angular/common/locales/es-PE').then(({ default: datos }) =>
          registerLocaleData(datos),
        ),
        firstValueFrom(sesion.cargar().pipe(catchError(() => of(null)))),
      ]);
    }),
    { provide: MAT_SNACK_BAR_DEFAULT_OPTIONS, useValue: { panelClass: 'aviso-transitorio' } },
    { provide: LOCALE_ID, useValue: 'es-PE' },
  ],
};
