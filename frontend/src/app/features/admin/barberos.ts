import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { finalize, map } from 'rxjs';
import { BarberosApi } from '../../core/api/barberos-api';
import { BarberoDto } from '../../core/modelos/catalogo';
import { ConfirmarEstadoDialogo } from '../../shared/confirmar-estado-dialogo';
import { mostrarErrores } from '../../shared/formulario';
import { BarberoDialogo } from './barbero-dialogo';

@Component({
  selector: 'app-barberos',
  imports: [MatButtonModule, MatSlideToggleModule, MatTableModule],
  templateUrl: './barberos.html',
  styleUrl: './catalogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Barberos {
  private readonly api = inject(BarberosApi);
  private readonly dialogos = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly filas = signal<BarberoDto[]>([]);
  readonly inactivos = signal(false);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly columnas = ['nombre', 'especialidad', 'correo', 'telefono', 'estado', 'acciones'];
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
  editar(barbero: BarberoDto | null = null) {
    this.dialogos
      .open(BarberoDialogo, {
        data: barbero,
        width: '540px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((guardado) => {
        if (guardado) this.cargar();
      });
  }
  cambiarEstado(barbero: BarberoDto) {
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
        data: {
          nombre: barbero.nombre,
          activar: !barbero.activo,
          cambiar: () =>
            this.api
              .cambiarEstado(barbero.id, !barbero.activo)
              .pipe(map((respuesta) => respuesta.reservasFuturasVigentes)),
        },
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((guardado) => {
        if (guardado) this.cargar();
      });
  }
}
