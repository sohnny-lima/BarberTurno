import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { finalize } from 'rxjs';
import { ReservasApi } from '../../core/api/reservas-api';
import { EstadoTransicion, ReservaDto } from '../../core/modelos/reservas';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { mostrarErrores } from '../../shared/formulario';

export const ACCIONES_TRANSICION: Record<EstadoTransicion, string> = {
  CONFIRMADA: 'Confirmar',
  EN_ATENCION: 'Iniciar atención',
  COMPLETADA: 'Completar',
  NO_ASISTIO: 'No asistió',
};
export type ResultadoAgenda = 'guardada' | 'actualizada';
export interface TransicionDatos {
  reserva: ReservaDto;
  estado: EstadoTransicion;
}
@Component({
  selector: 'app-transicion-dialogo',
  imports: [MatButtonModule, MatDialogModule, FechaLimaPipe],
  templateUrl: './transicion-dialogo.html',
  styleUrl: './dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransicionDialogo {
  readonly datos = inject<TransicionDatos>(MAT_DIALOG_DATA);
  readonly referencia = inject(MatDialogRef<TransicionDialogo, ResultadoAgenda>);
  private readonly api = inject(ReservasApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly accion = ACCIONES_TRANSICION[this.datos.estado];
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  confirmar() {
    if (this.guardando()) return;
    this.guardando.set(true);
    this.referencia.disableClose = true;
    this.mensaje.set('');
    this.api
      .transicionar(this.datos.reserva.id, {
        estado: this.datos.estado,
        version: this.datos.reserva.version,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.referencia.disableClose = false;
        }),
      )
      .subscribe({
        next: () => this.referencia.close('guardada'),
        error: (error: HttpErrorResponse) => {
          if (error.status === 409) this.referencia.close('actualizada');
          else this.mensaje.set(mostrarErrores(new FormGroup({}), error));
        },
      });
  }
}
