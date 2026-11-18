import { Routes } from '@angular/router';
import { passwordGuard } from './core/auth/guards';
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    canActivateChild: [passwordGuard],
    loadChildren: () => import('./shell.routes').then((m) => m.rutasShell),
  },
];
