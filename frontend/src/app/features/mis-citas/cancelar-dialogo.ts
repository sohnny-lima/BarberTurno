import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { ReservasApi } from '../../core/api/reservas-api';
import { ReservaDto } from '../../core/modelos/reservas';
import { motivoObligatorio } from '../../shared/motivo-obligatorio';
import { mostrarErrores } from '../../shared/formulario';

export type ResultadoCancelacion = 'cancelada' | 'actualizada';
@Component({
  selector: 'app-cancelar-dialogo',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './cancelar-dialogo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CancelarDialogo {
  readonly reserva = inject<ReservaDto & { motivoObligatorio?: boolean }>(MAT_DIALOG_DATA);
  readonly referencia = inject(MatDialogRef<CancelarDialogo, ResultadoCancelacion>);
  private readonly api = inject(ReservasApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly formulario = new FormGroup({
    motivo: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.maxLength(300),
        ...(this.reserva.motivoObligatorio ? [motivoObligatorio] : []),
      ],
    }),
  });
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  confirmar() {
    if (this.guardando()) return;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    this.guardando.set(true);
    this.referencia.disableClose = true;
    this.mensaje.set('');
    const motivo = this.formulario.controls.motivo.value.trim();
    this.api
      .cancelar(this.reserva.id, {
        version: this.reserva.version,
        ...(motivo ? { motivo } : {}),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.referencia.disableClose = false;
        }),
      )
      .subscribe({
        next: () => this.referencia.close('cancelada'),
        error: (error: HttpErrorResponse) => {
          if (error.status === 409) {
            this.referencia.close('actualizada');
          } else {
            this.mensaje.set(mostrarErrores(this.formulario, error));
          }
        },
      });
  }
}
