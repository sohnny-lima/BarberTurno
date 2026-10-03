import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { finalize, map } from 'rxjs';
import { ServiciosApi } from '../../core/api/servicios-api';
import { ServicioDto } from '../../core/modelos/catalogo';
import { ConfirmarEstadoDialogo } from '../../shared/confirmar-estado-dialogo';
import { mostrarErrores } from '../../shared/formulario';
import { ServicioDialogo } from './servicio-dialogo';

@Component({
  selector: 'app-servicios',
  imports: [CurrencyPipe, MatButtonModule, MatSlideToggleModule, MatTableModule],
  templateUrl: './servicios.html',
  styleUrl: './catalogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Servicios {
  private readonly api = inject(ServiciosApi);
  private readonly dialogos = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly filas = signal<ServicioDto[]>([]);
  readonly inactivos = signal(false);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly columnas = ['nombre', 'duracion', 'precio', 'estado', 'acciones'];
  constructor() {
    this.cargar();
  }
  cargar(inactivos = this.inactivos()) {
    if (this.cargando()) return;
    this.inactivos.set(inactivos);
    this.cargando.set(true);
    this.mensaje.set('');
    this.api
      .listar(inactivos)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (filas) => this.filas.set(filas),
        error: (error) => this.mensaje.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
  editar(servicio: ServicioDto | null = null) {
    this.dialogos
      .open(ServicioDialogo, {
        data: servicio,
        width: '520px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((guardado) => {
        if (guardado) this.cargar();
      });
  }
  cambiarEstado(servicio: ServicioDto) {
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
        data: {
          nombre: servicio.nombre,
          activar: !servicio.activo,
          cambiar: () => this.api.cambiarEstado(servicio.id, !servicio.activo).pipe(map(() => 0)),
        },
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((guardado) => {
        if (guardado) this.cargar();
      });
  }
}
