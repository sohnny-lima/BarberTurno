import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { EMPTY, filter, takeUntil } from 'rxjs';
import { NegocioActual, ORIGEN_NEGOCIO } from './negocio-actual';

/** Cancela el transporte y verifica también el origen antes de propagar una respuesta. */
export const negocioInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  if (!peticion.url.startsWith('/api/n/')) {
    return siguiente(peticion.clone({ headers: peticion.headers.delete('X-BT-Papel') }));
  }
  const negocio = inject(NegocioActual);
  const origen = peticion.context.get(ORIGEN_NEGOCIO);
  if (
    !origen ||
    !negocio.vigente(origen) ||
    !peticion.url.startsWith('/api/n/' + origen.slug + '/')
  )
    return EMPTY;
  const headers = peticion.headers.delete('X-BT-Papel');
  return siguiente(
    peticion.clone({
      headers: origen.papel === 'CLIENTE' ? headers.set('X-BT-Papel', 'CLIENTE') : headers,
    }),
  ).pipe(
    takeUntil(negocio.cambios$),
    filter(() => negocio.vigente(origen)),
  );
};
