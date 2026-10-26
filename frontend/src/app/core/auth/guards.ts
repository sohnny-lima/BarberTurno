import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol } from '../modelos/identidad';
import { SesionService } from './sesion-service';
import { NegocioActual } from '../negocio/negocio-actual';
import { RolPersonal } from '../modelos/negocios';

export function gestionGuard(roles: RolPersonal[]): CanActivateFn {
  const efectivo = rolGuard(['ADMIN']);
  return (ruta, estado) => {
    const resultado = efectivo(ruta, estado);
    if (resultado !== true) return resultado;
    const negocio = inject(NegocioActual);
    if (
      ['inexistente', 'error', 'suspendido'].includes(negocio.estado()?.tipo ?? '') &&
      !negocio.rol()
    )
      return true;
    return (
      roles.includes(negocio.usuario()?.rolNegocio as RolPersonal) ||
      inject(Router).parseUrl(negocio.enlace('sin-acceso'))
    );
  };
}

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
  return (_ruta, estado) => {
    const sesion = inject(SesionService);
    const negocio = inject(NegocioActual);
    const router = inject(Router);
    if (sesion.debeCambiarPassword()) return router.createUrlTree(['/cambiar-password']);
    // El contenedor muestra estos estados y no crea la sección protegida.
    if (
      ['inexistente', 'error', 'suspendido'].includes(negocio.estado()?.tipo ?? '') &&
      !negocio.rol()
    )
      return true;
    if (!negocio.usuario())
      return router.createUrlTree(['/ingresar'], { queryParams: { returnUrl: estado.url } });
    return roles.includes(negocio.rol()!) || router.parseUrl(negocio.enlace('sin-acceso'));
  };
}

export const legadoGuard: CanActivateFn = () => {
  return inject(Router).parseUrl(inject(SesionService).autenticado() ? '/negocios' : '/ingresar');
};
