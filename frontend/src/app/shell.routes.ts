import { inject } from '@angular/core';
import { Routes, Router } from '@angular/router';
import { authGuard, legadoGuard, plataformaGuard } from './core/auth/guards';
import { SesionService } from './core/auth/sesion-service';
import { negocioGuard } from './core/negocio/rutas-negocio';
const proximamente = () => import('./features/proximamente').then((m) => m.Proximamente);
export const rutasShell: Routes = [
  {
    path: 'sin-acceso',
    loadComponent: () => import('./features/negocios/sin-acceso').then((m) => m.SinAcceso),
  },
  {
    path: 'plataforma',
    canActivate: [plataformaGuard],
    canActivateChild: [plataformaGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'negocios' },
      {
        path: 'negocios',
        loadComponent: () => import('./features/plataforma/plataforma').then((m) => m.Plataforma),
      },
      { path: '**', redirectTo: 'negocios' },
    ],
  },
  {
    path: 'invitacion/:token',
    loadComponent: () => import('./features/gestion/aceptacion').then((m) => m.Aceptacion),
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [
      () => {
        const sesion = inject(SesionService);
        return inject(Router).parseUrl(sesion.autenticado() ? sesion.inicio() : '/ingresar');
      },
    ],
    loadComponent: proximamente,
  },
  {
    path: 'ingresar',
    loadComponent: () => import('./features/auth/ingresar').then((modulo) => modulo.Ingresar),
  },
  {
    path: 'registro',
    loadComponent: () => import('./features/auth/registro').then((modulo) => modulo.Registro),
  },
  {
    path: 'privacidad',
    loadComponent: () => import('./features/auth/privacidad').then((modulo) => modulo.Privacidad),
  },
  {
    path: 'cambiar-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/cambiar-password').then((modulo) => modulo.CambiarPassword),
  },
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () => import('./features/perfil/perfil').then((modulo) => modulo.Perfil),
  },
  {
    path: 'negocios',
    canActivate: [authGuard],
    loadComponent: () => import('./features/negocios/negocios').then((m) => m.Negocios),
  },
  {
    path: 'n/:slug',
    canActivate: [negocioGuard],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('./features/negocios/negocio-vista').then((m) => m.NegocioVista),
    loadChildren: () => import('./features/negocios/negocio.routes').then((m) => m.rutasNegocio),
  },
  { path: '**', canActivate: [legadoGuard], loadComponent: proximamente },
];
