import { inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CanActivateFn, NavigationStart, Router } from '@angular/router';
import { filter, map } from 'rxjs';
import { NegocioActual } from './negocio-actual';

/** Invalida de forma síncrona al empezar a navegar, antes de reutilizar o destruir cualquier vista. */
@Injectable({ providedIn: 'root' })
export class RutasNegocio {
  constructor() {
    const router = inject(Router);
    const negocio = inject(NegocioActual);
    router.events
      .pipe(
        filter((evento) => evento instanceof NavigationStart),
        takeUntilDestroyed(),
      )
      .subscribe((evento) => {
        const arbol = router.parseUrl(evento.url);
        const segmentos = arbol.root.children['primary']?.segments ?? [];
        negocio.seleccionar(
          segmentos[0]?.path === 'n' ? (segmentos[1]?.path ?? null) : null,
          arbol.queryParams['papel'] === 'cliente' ? 'CLIENTE' : 'PERSONAL',
        );
      });
  }
}

export const negocioGuard: CanActivateFn = (ruta) => {
  const negocio = inject(NegocioActual);
  negocio.seleccionar(
    ruta.paramMap.get('slug')!,
    ruta.queryParamMap.get('papel') === 'cliente' ? 'CLIENTE' : 'PERSONAL',
  );
  return negocio.cargar().pipe(map(() => true));
};
