import { Routes } from '@angular/router';
import { gestionGuard, rolGuard } from '../../core/auth/guards';
export const rutasNegocio: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'reservar' },
  {
    path: 'reservar',
    loadComponent: () => import('../reservar/reservar').then((modulo) => modulo.Reservar),
    data: { titulo: 'Reservar un turno' },
  },
  {
    path: 'mis-citas',
    canActivate: [rolGuard(['CLIENTE'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../mis-citas/mis-citas').then((modulo) => modulo.MisCitas),
    data: { titulo: 'Mis citas' },
  },
  {
    path: 'agenda',
    canActivate: [rolGuard(['BARBERO', 'ADMIN'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../agenda/agenda').then((modulo) => modulo.Agenda),
    data: { titulo: 'Agenda' },
  },
  {
    path: 'admin/servicios',
    canActivate: [rolGuard(['ADMIN'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../admin/servicios').then((modulo) => modulo.Servicios),
  },
  {
    path: 'admin/barberos',
    canActivate: [rolGuard(['ADMIN'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../admin/barberos').then((modulo) => modulo.Barberos),
  },
  {
    path: 'admin/horarios',
    canActivate: [rolGuard(['ADMIN'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../admin/horarios').then((modulo) => modulo.Horarios),
  },
  {
    path: 'admin/reportes',
    canActivate: [rolGuard(['ADMIN'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../admin/reportes').then((modulo) => modulo.Reportes),
  },
  {
    path: 'admin/usuarios',
    canActivate: [rolGuard(['ADMIN'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../admin/usuarios').then((modulo) => modulo.Usuarios),
  },
  {
    path: 'equipo',
    canActivate: [gestionGuard(['PROPIETARIO', 'ENCARGADO'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../gestion/equipo').then((m) => m.Equipo),
  },
  {
    path: 'invitaciones',
    canActivate: [gestionGuard(['PROPIETARIO', 'ENCARGADO'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../gestion/invitaciones').then((m) => m.Invitaciones),
  },
  {
    path: 'configuracion',
    canActivate: [gestionGuard(['PROPIETARIO'])],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('../gestion/configuracion').then((m) => m.Configuracion),
  },
  { path: 'sin-acceso', loadComponent: () => import('./sin-acceso').then((m) => m.SinAcceso) },
  { path: '**', redirectTo: 'sin-acceso' },
];
