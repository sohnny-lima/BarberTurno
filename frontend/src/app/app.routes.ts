import { inject } from '@angular/core';
import { Routes, Router } from '@angular/router';
import { authGuard, passwordGuard, rolGuard } from './core/auth/guards';
import { SesionService } from './core/auth/sesion-service';

const proximamente = () => import('./features/proximamente').then((modulo) => modulo.Proximamente);
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((modulo) => modulo.Shell),
    canActivateChild: [passwordGuard],
    children: [
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
        loadComponent: () =>
          import('./features/auth/privacidad').then((modulo) => modulo.Privacidad),
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
        path: 'reservar',
        loadComponent: () =>
          import('./features/reservar/reservar').then((modulo) => modulo.Reservar),
        data: { titulo: 'Reservar un turno' },
      },
      {
        path: 'mis-citas',
        canActivate: [authGuard, rolGuard(['CLIENTE'])],
        loadComponent: () =>
          import('./features/mis-citas/mis-citas').then((modulo) => modulo.MisCitas),
        data: { titulo: 'Mis citas' },
      },
      {
        path: 'agenda',
        canActivate: [authGuard, rolGuard(['BARBERO', 'ADMIN'])],
        loadComponent: () => import('./features/agenda/agenda').then((modulo) => modulo.Agenda),
        data: { titulo: 'Agenda' },
      },
      {
        path: 'admin/servicios',
        canActivate: [authGuard, rolGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/servicios').then((modulo) => modulo.Servicios),
      },
      {
        path: 'admin/barberos',
        canActivate: [authGuard, rolGuard(['ADMIN'])],
        loadComponent: () => import('./features/admin/barberos').then((modulo) => modulo.Barberos),
      },
      {
        path: 'admin/horarios',
        canActivate: [authGuard, rolGuard(['ADMIN'])],
        loadComponent: () => import('./features/admin/horarios').then((modulo) => modulo.Horarios),
      },
      {
        path: 'admin/reportes',
        canActivate: [authGuard, rolGuard(['ADMIN'])],
        loadComponent: () => import('./features/admin/reportes').then((modulo) => modulo.Reportes),
      },
      ...['usuarios'].map((recurso) => ({
        path: 'admin/' + recurso,
        canActivate: [authGuard, rolGuard(['ADMIN'])],
        loadComponent: proximamente,
        data: { titulo: recurso.charAt(0).toUpperCase() + recurso.slice(1) },
      })),
      { path: '**', redirectTo: '' },
    ],
  },
];
