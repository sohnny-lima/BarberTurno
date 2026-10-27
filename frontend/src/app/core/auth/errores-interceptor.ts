import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, takeUntil, throwError } from 'rxjs';
import { ProblemDetail } from '../modelos/identidad';
import { SesionService } from './sesion-service';
import { NegocioActual, CONSULTA_CONTEXTO, ORIGEN_NEGOCIO } from '../negocio/negocio-actual';
import { HttpClient } from '@angular/common/http';
import { UsuarioSesionDto } from '../modelos/identidad';
import { estaPresentado } from '../notificaciones/errores-presentados';

export const erroresInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const snackbar = inject(MatSnackBar);
  const negocio = inject(NegocioActual);
  const http = inject(HttpClient);
  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      const problema = error.error as Partial<ProblemDetail> | null;
      const ruta = peticion.url.split('?')[0];
      const origen = peticion.context.get(ORIGEN_NEGOCIO);
      if (origen && !negocio.vigente(origen)) return throwError(() => error);
      if (peticion.context.get(CONSULTA_CONTEXTO)) return throwError(() => error);
      if (error.status === 401 && ruta === '/api/auth/sesion' && peticion.method === 'GET') {
        return throwError(() => error);
      }
      if (error.status === 401 && ruta !== '/api/auth/login') {
        sesion.limpiar();
        void router.navigate(['/ingresar'], { queryParams: { returnUrl: router.url } });
      } else if (error.status === 403 && problema?.codigo === 'CAMBIO_PASSWORD_REQUERIDO') {
        void router.navigate(['/cambiar-password']);
      } else if (error.status === 403 && origen) {
        // Un 403 por permisos de sección no implica revocación: se revalida el rol efectivo.
        const nombre = negocio.nombre();
        const eraPersonal = !!negocio.usuario()?.rolNegocio;
        http
          .get<UsuarioSesionDto>(negocio.url('/sesion'), { context: negocio.contexto(true) })
          .pipe(takeUntil(negocio.cambios$))
          .subscribe({
            next: (usuario) => {
              if (!negocio.vigente(origen)) return;
              negocio.actualizarSesion(usuario);
              if (!usuario.rol || (eraPersonal && !usuario.rolNegocio)) {
                // Si conserva su ficha, el selector la sigue mostrando: el aviso no lo contradice.
                negocio.avisoSelector.set(
                  eraPersonal && usuario.rol === 'CLIENTE'
                    ? 'Ya no tiene acceso como personal a ' +
                        nombre +
                        '. Puede seguir entrando como cliente.'
                    : 'Ya no tiene acceso a ' + nombre + '.',
                );
                void router.navigate(['/negocios']);
              } else snackbar.open('No tiene acceso a esta sección.', 'Cerrar', { duration: 6000 });
            },
            error: (consulta: HttpErrorResponse) => {
              if (consulta.status === 401 && negocio.vigente(origen)) {
                const retorno = router.url;
                sesion.limpiar();
                void router.navigate(['/ingresar'], { queryParams: { returnUrl: retorno } });
              }
            },
          });
      } else if (error.status === 409 && problema?.codigo === 'NEGOCIO_SUSPENDIDO') {
        // La vista conserva el rechazo junto a la acción; si la suspensión llegó con la
        // sesión abierta, el aviso fijo aparece sin esperar a recargar. Sin snackbar duplicado.
        if (origen) negocio.marcarSuspendido();
      } else {
        const mensaje =
          error.status === 0 || error.status >= 500
            ? 'No pudimos completar la solicitud. Intente nuevamente.'
            : problema?.detail || 'No pudimos completar la solicitud.';
        // Diferido: si la vista muestra el error junto a la acción (marcarPresentado en su
        // manejador, que se ejecuta antes), no se repite en un snackbar.
        queueMicrotask(() => {
          if (!estaPresentado(error)) snackbar.open(mensaje, 'Cerrar', { duration: 6000 });
        });
      }
      return throwError(() => error);
    }),
  );
};
