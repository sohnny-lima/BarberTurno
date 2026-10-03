import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { ServiciosApi } from '../../core/api/servicios-api';
import { ServicioDto } from '../../core/modelos/catalogo';
import { errorCampo, mostrarErrores } from '../../shared/formulario';
import { formularioServicio } from './validadores-catalogo';

@Component({
  selector: 'app-servicio-dialogo',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './servicio-dialogo.html',
  styleUrl: './catalogo-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicioDialogo {
  readonly servicio = inject<ServicioDto | null>(MAT_DIALOG_DATA);
  readonly referencia = inject(MatDialogRef<ServicioDialogo, boolean>);
  private readonly api = inject(ServiciosApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly formulario = formularioServicio(this.servicio);
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  readonly errorCampo = errorCampo;
  guardar() {
    if (this.guardando()) return;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    this.guardando.set(true);
    this.referencia.disableClose = true;
    this.mensaje.set('');
    const datos = this.formulario.getRawValue();
    const peticion = this.servicio
      ? this.api.editar(this.servicio.id, datos)
      : this.api.crear(datos);
    peticion
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.referencia.disableClose = false;
        }),
      )
      .subscribe({
        next: () => this.referencia.close(true),
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
}
