import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ProblemDetail } from '../modelos/identidad';
import { SesionService } from './sesion-service';

export const erroresInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const snackbar = inject(MatSnackBar);
  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      const problema = error.error as Partial<ProblemDetail> | null;
      const ruta = peticion.url.split('?')[0];
      if (
        error.status === 401 &&
        ((ruta === '/api/auth/sesion' && peticion.method === 'GET') ||
          (ruta === '/api/auth/login' && problema?.codigo === 'NO_AUTENTICADO'))
      ) {
        return throwError(() => error);
      }
      if (error.status === 401 && ruta !== '/api/auth/login' && ruta !== '/api/auth/registro') {
        sesion.limpiar();
        void router.navigate(['/ingresar'], { queryParams: { returnUrl: router.url } });
      } else if (error.status === 403 && problema?.codigo === 'CAMBIO_PASSWORD_REQUERIDO') {
        void router.navigate(['/cambiar-password']);
      } else {
        const mensaje =
          error.status === 0 || error.status >= 500
            ? 'No pudimos completar la solicitud. Intente nuevamente.'
            : problema?.detail || 'No pudimos completar la solicitud.';
        snackbar.open(mensaje, 'Cerrar', { duration: 6000 });
      }
      return throwError(() => error);
    }),
  );
};
