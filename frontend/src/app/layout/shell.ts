import { RutasNegocio } from '../core/negocio/rutas-negocio';
import { NegocioActual } from '../core/negocio/negocio-actual';
import { NOMBRES_PAPEL } from '../core/modelos/negocios';
import { BarberiaActual } from './barberia-actual';
import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MAT_DIALOG_DEFAULT_OPTIONS, MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';

import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';

import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, finalize, map } from 'rxjs';
import { SesionService } from '../core/auth/sesion-service';

import { TituloPagina } from '../shared/titulo-pagina';
import { RegionMenu } from '../shared/region-menu';
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
  imports: [
    BarberiaActual,
    MatButtonModule,
    MatMenuModule,
    RegionMenu,
    MatSidenavModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
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
  readonly negocio = inject(NegocioActual);
  readonly avisos = inject(AvisosService);
  private readonly dialogos = inject(MatDialog);
  private dialogoAvisosActivo = false;
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly menu = viewChild(MatSidenav);
  private readonly contenido = viewChild<ElementRef<HTMLElement>>('contenido');
  readonly saliendo = signal(false);
  readonly ruta = toSignal(
    this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );
  readonly navegacionCompleta = computed(
    () => this.sesion.autenticado() && !this.sesion.debeCambiarPassword(),
  );
  readonly tieneBarraInferior = computed(
    () =>
      this.movil() &&
      this.navegacionCompleta() &&
      this.negocio.rol() !== 'ADMIN' &&
      !this.zonaPlataforma(),
  );
  readonly puedePlataforma = computed(
    () =>
      this.sesion.usuario()?.rolPlataforma === 'ADMIN_PLATAFORMA' &&
      !this.sesion.debeCambiarPassword(),
  );
  readonly zonaPlataforma = computed(() => this.ruta().startsWith('/plataforma'));
  readonly usaCajon = computed(() => this.negocio.rol() === 'ADMIN' || this.zonaPlataforma());
  readonly tituloVista = inject(TituloPagina);
  readonly tituloPagina = computed(() => {
    if (this.tituloVista.texto()) return this.tituloVista.texto()!;
    const ruta = this.ruta().split('?')[0];
    if (ruta.endsWith('/reservar'))
      return this.negocio.rol() === 'ADMIN' ? 'Reserva asistida' : 'Reservar un turno';
    return this.enlaces().find((enlace) => enlace.ruta === ruta)?.texto ?? 'BarberTurno';
  });
  readonly iniciales = computed(() =>
    (this.sesion.usuario()?.nombre ?? '')
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((nombre) => nombre[0])
      .join('')
      .toUpperCase(),
  );
  readonly movil = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 767.98px)')
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );
  readonly enlaces = computed(() => {
    if (this.sesion.debeCambiarPassword())
      return [{ ruta: '/cambiar-password', texto: 'Cambiar contraseña' }];
    const prefijar = (filas: { ruta: string; texto: string }[]) =>
      filas.map((fila) =>
        fila.ruta === '/perfil' ? fila : { ...fila, ruta: this.negocio.ruta(fila.ruta.slice(1)) },
      );
    switch (this.negocio.rol()) {
      case 'CLIENTE':
        return prefijar([
          { ruta: '/reservar', texto: 'Reservar' },
          { ruta: '/mis-citas', texto: 'Mis citas' },
          CUENTA,
        ]);
      case 'BARBERO':
        return prefijar([AGENDA, CUENTA]);
      case 'ADMIN':
        return prefijar([
          ...ADMIN.slice(0, -1),
          ...(this.negocio.usuario()?.rolNegocio === 'PROPIETARIO' ||
          this.negocio.usuario()?.rolNegocio === 'ENCARGADO'
            ? [
                { ruta: '/equipo', texto: 'Equipo' },
                { ruta: '/invitaciones', texto: 'Invitaciones' },
              ]
            : []),
          ...(this.negocio.usuario()?.rolNegocio === 'PROPIETARIO'
            ? [{ ruta: '/configuracion', texto: 'Configuración' }]
            : []),
          CUENTA,
        ]);
      default:
        if (this.sesion.autenticado())
          return [{ ruta: '/negocios', texto: 'Sus barberías' }, CUENTA];
        return [
          { ruta: '/ingresar', texto: 'Ingresar' },
          { ruta: '/registro', texto: 'Crear cuenta' },
        ];
    }
  });
  readonly nombreRol = computed(() => {
    if (!this.negocio.slug())
      return this.puedePlataforma() ? 'Administración de la plataforma' : '';
    const rol = this.negocio.usuario()?.rolNegocio;
    return this.negocio.rol() === 'CLIENTE' ? 'Cliente' : rol ? NOMBRES_PAPEL[rol] : '';
  });
  parametrosEnlace(ruta: string) {
    if ((ruta === '/registro' || ruta === '/ingresar') && this.negocio.slug())
      return { returnUrl: this.negocio.ruta('reservar') };
    return ruta.startsWith('/n/') && this.negocio.papel() === 'CLIENTE' ? { papel: 'cliente' } : {};
  }
  constructor() {
    inject(RutasNegocio);
    this.negocio.cambios$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.dialogos.closeAll();
      this.dialogoAvisosActivo = false;
    });
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        const menu = this.menu();
        if (!this.movil() || !menu?.opened) {
          this.contenido()?.nativeElement.focus();
          return;
        }
        void menu.close().then(() => {
          // Material puede restaurar el disparador; la navegación sitúa el foco en su destino.
          if (!this.destroyRef.destroyed && this.movil()) {
            this.contenido()?.nativeElement.focus();
          }
        });
      });
  }
  /**
   * Salto al contenido sin navegar: con `<base href="/">`, «#contenido» se resolvería como
   * «/#contenido» y llevaría a la raíz. Con el menú móvil abierto, primero lo cierra.
   */
  saltarAlContenido(evento: Event) {
    evento.preventDefault();
    // focus() también desplaza la vista hasta el contenido.
    const enfocar = () => this.contenido()?.nativeElement.focus();
    const menu = this.menu();
    if (this.movil() && menu?.opened) void menu.close().then(enfocar);
    else enfocar();
  }
  async abrirAvisos() {
    if (this.dialogoAvisosActivo) return;
    const origen = this.negocio.ambito();
    if (!origen) return;
    this.dialogoAvisosActivo = true;
    try {
      const { AvisosDialogo } = await import('./avisos-dialogo');
      if (!this.negocio.vigente(origen) || this.destroyRef.destroyed) return;
      this.dialogos
        .open(AvisosDialogo, {
          width: '640px',
          maxWidth: 'calc(100vw - 32px)',
          maxHeight: 'calc(100dvh - 32px)',
          autoFocus: '[mat-dialog-close]',
          restoreFocus: true,
          ariaLabelledBy: 'titulo-dialogo-avisos',
        })
        .afterClosed()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          this.dialogoAvisosActivo = false;
        });
    } catch (error) {
      this.dialogoAvisosActivo = false;
      throw error;
    }
  }
  icono(ruta: string) {
    if (ruta.endsWith('/equipo'))
      return 'M15 8a3 3 0 1 1-6 0 3 3 0 0 1 6 0M5 21v-2a7 7 0 0 1 14 0v2M18 4a3 3 0 0 1 0 6M21 20v-2a6 6 0 0 0-3-5';
    if (ruta.endsWith('/invitaciones')) return 'M3 5h18v14H3zM3 6l9 7 9-7';
    if (ruta.endsWith('/configuracion'))
      return 'M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1zM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0';
    if (ruta.startsWith('/plataforma') || ruta === '/negocios')
      return 'M3 9l2-6h14l2 6v3H3zM5 12v9h14v-9M9 21v-6h6v6M8 3v6M16 3v6';
    if (ruta.endsWith('/agenda')) return 'M4 5h16v16H4zM8 3v4M16 3v4M4 10h16M8 14h2M14 14h2M8 18h2';
    if (ruta === '/perfil' || ruta.endsWith('/usuarios') || ruta.endsWith('/barberos'))
      return 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M4 21v-2a8 8 0 0 1 16 0v2';
    if (ruta.endsWith('/mis-citas') || ruta.endsWith('/reportes'))
      return 'M6 3h12v18H6zM9 7h6M9 11h6M9 15h4';
    if (ruta.endsWith('/servicios'))
      return 'M9 7a3 3 0 1 1-6 0 3 3 0 0 1 6 0M9 17a3 3 0 1 1-6 0 3 3 0 0 1 6 0M8 9l13 11M8 15L21 4';
    if (ruta.endsWith('/horarios')) return 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0M12 7v5l4 2';
    return 'M4 5h16v16H4zM8 3v4M16 3v4M4 10h16M12 13v5M9 15h6';
  }
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
