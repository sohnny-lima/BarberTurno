import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol } from '../modelos/identidad';
import { SesionService } from './sesion-service';

export const authGuard: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  return (
    sesion.autenticado() ||
    inject(Router).createUrlTree(['/ingresar'], { queryParams: { returnUrl: estado.url } })
  );
};
export const passwordGuard: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  return (
    !sesion.debeCambiarPassword() ||
    estado.url.split('?')[0] === '/cambiar-password' ||
    inject(Router).createUrlTree(['/cambiar-password'])
  );
};
export function rolGuard(roles: Rol[]): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const router = inject(Router);
    if (!sesion.autenticado()) return router.createUrlTree(['/ingresar']);
    if (sesion.debeCambiarPassword()) return router.createUrlTree(['/cambiar-password']);
    return roles.includes(sesion.rol()!) || router.createUrlTree([sesion.inicio()]);
  };
}
