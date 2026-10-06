import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MAT_DIALOG_DEFAULT_OPTIONS, MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import { MatSidenavModule } from '@angular/material/sidenav';

import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize, map } from 'rxjs';
import { SesionService } from '../core/auth/sesion-service';

import { AvisosService } from '../core/notificaciones/avisos-service';

const CUENTA = { ruta: '/perfil', texto: 'Mi cuenta' };
const AGENDA = { ruta: '/agenda', texto: 'Agenda' };
const ADMIN = [
  AGENDA,
  { ruta: '/reservar', texto: 'Reserva asistida' },
  { ruta: '/admin/servicios', texto: 'Servicios' },
  { ruta: '/admin/barberos', texto: 'Barberos' },
  { ruta: '/admin/horarios', texto: 'Horarios' },
  { ruta: '/admin/reportes', texto: 'Reportes' },
  { ruta: '/admin/usuarios', texto: 'Usuarios' },
  CUENTA,
];

@Component({
  selector: 'app-shell',
  imports: [MatButtonModule, MatSidenavModule, RouterLink, RouterLinkActive, RouterOutlet],
  // Los defaults visuales se cargan con el shell lazy, incluidos sus diálogos.
  providers: [
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
    MatDialog,
    {
      provide: MAT_DIALOG_DEFAULT_OPTIONS,
      useFactory: () => ({ ...new MatDialogConfig(), injector: inject(Injector) }),
    },
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  readonly sesion = inject(SesionService);
  readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly saliendo = signal(false);
  readonly movil = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 767.98px)')
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );
  readonly enlaces = computed(() => {
    if (this.sesion.debeCambiarPassword())
      return [{ ruta: '/cambiar-password', texto: 'Cambiar contraseña' }];
    switch (this.sesion.rol()) {
      case 'CLIENTE':
        return [
          { ruta: '/reservar', texto: 'Reservar' },
          { ruta: '/mis-citas', texto: 'Mis citas' },
          CUENTA,
        ];
      case 'BARBERO':
        return [AGENDA, CUENTA];
      case 'ADMIN':
        return ADMIN;
      default:
        return [
          { ruta: '/ingresar', texto: 'Ingresar' },
          { ruta: '/registro', texto: 'Crear cuenta' },
        ];
    }
  });
  readonly nombreRol = computed(
    () =>
      ({ CLIENTE: 'Cliente', BARBERO: 'Barbero', ADMIN: 'Administrador' })[
        this.sesion.rol() ?? 'CLIENTE'
      ],
  );
  salir() {
    if (this.saliendo()) return;
    this.saliendo.set(true);
    this.sesion
      .logout()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saliendo.set(false)),
      )
      .subscribe({
        next: () => {
          void this.router.navigate(['/ingresar']);
        },
        error: () => {
          /* El interceptor muestra el error y permite reintentar. */
        },
      });
  }
}
