import { ChangeDetectionStrategy, Component, inject, Injector } from '@angular/core';
import { MAT_DIALOG_DEFAULT_OPTIONS, MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { BarberosApi } from '../../core/api/barberos-api';
import { DisponibilidadApi } from '../../core/api/disponibilidad-api';
import { HorariosApi } from '../../core/api/horarios-api';
import { NotificacionesApi } from '../../core/api/notificaciones-api';
import { ReportesApi } from '../../core/api/reportes-api';
import { ReservasApi } from '../../core/api/reservas-api';
import { ServiciosApi } from '../../core/api/servicios-api';
import { UsuariosApi } from '../../core/api/usuarios-api';
import { VinculoProfesionalApi } from '../../core/api/vinculo-profesional-api';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { EstadoContenido } from '../../shared/estado-contenido';

@Component({
  selector: 'app-negocio-vista',
  imports: [RouterOutlet, RouterLink, MatButtonModule, CabeceraPagina, EstadoContenido],
  providers: [
    BarberosApi,
    DisponibilidadApi,
    HorariosApi,
    NotificacionesApi,
    ReportesApi,
    ReservasApi,
    ServiciosApi,
    UsuariosApi,
    VinculoProfesionalApi,
    MatDialog,
    {
      provide: MAT_DIALOG_DEFAULT_OPTIONS,
      useFactory: () => ({ ...new MatDialogConfig(), injector: inject(Injector) }),
    },
  ],
  template: ` @if (negocio.estado()?.tipo === 'inexistente') {
      <app-cabecera-pagina titulo="Barbería no encontrada" />
      <app-estado-contenido tipo="vacio" texto="No encontramos esa barbería. Revise la dirección."
        ><a mat-button routerLink="/negocios">Ir a mis barberías</a></app-estado-contenido
      >
    } @else if (negocio.estado()?.tipo === 'error') {
      <app-cabecera-pagina [titulo]="negocio.nombre() ?? ''" />
      <app-estado-contenido
        tipo="error"
        texto="No pudimos consultar la barbería. Intente nuevamente."
        reintento="Reintentar"
        (reintentar)="recargar()"
      />
    } @else if (negocio.estado()?.tipo === 'suspendido' && !personal()) {
      <app-cabecera-pagina titulo="Barbería no disponible" />
      <app-estado-contenido tipo="aviso" texto="Esta barbería no está disponible por ahora."
        ><a mat-button routerLink="/negocios">Ir a mis barberías</a></app-estado-contenido
      >
    } @else if (negocio.estado()?.tipo === 'cargando') {
      <app-estado-contenido tipo="carga" texto="Cargando barbería…" />
    } @else {
      @if (negocio.estado()?.tipo === 'suspendido') {
        <app-estado-contenido
          tipo="aviso"
          texto="Barbería suspendida: puede consultar, pero no hacer cambios. Las acciones de cambio quedan desactivadas hasta que la plataforma la reactive."
        />
      }
      <router-outlet />
    }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NegocioVista {
  readonly negocio = inject(NegocioActual);
  readonly personal = () => ['ADMIN', 'BARBERO'].includes(this.negocio.rol() ?? '');
  recargar() {
    this.negocio.cargar().subscribe();
  }
}
