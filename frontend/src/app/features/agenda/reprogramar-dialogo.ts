import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, Subscription } from 'rxjs';
import { DisponibilidadApi } from '../../core/api/disponibilidad-api';
import { ReservasApi } from '../../core/api/reservas-api';
import { BarberoDto } from '../../core/modelos/catalogo';
import { FranjaDto, ReservaDto } from '../../core/modelos/reservas';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';
import { mostrarErrores } from '../../shared/formulario';
import { motivoObligatorio } from '../../shared/motivo-obligatorio';
import { ResultadoAgenda } from './transicion-dialogo';

export interface ReprogramarDatos {
  reserva: ReservaDto;
  barberos: BarberoDto[];
}
@Component({
  selector: 'app-reprogramar-dialogo',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    FechaLimaPipe,
  ],
  templateUrl: './reprogramar-dialogo.html',
  styleUrl: './reprogramar-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReprogramarDialogo {
  readonly datos = inject<ReprogramarDatos>(MAT_DIALOG_DATA);
  readonly referencia = inject(MatDialogRef<ReprogramarDialogo, ResultadoAgenda>);
  private readonly disponibilidad = inject(DisponibilidadApi);
  private readonly api = inject(ReservasApi);
  private readonly destroyRef = inject(DestroyRef);
  private peticion?: Subscription;
  readonly min = fechaHoyLima();
  readonly max = fechaHoyLima(30);
  readonly formulario = new FormGroup({
    fecha: new FormControl(fechaHoyLima(0, new Date(this.datos.reserva.inicio)), {
      nonNullable: true,
      validators: [Validators.required],
    }),
    barberoId: new FormControl<number | null>(
      this.datos.barberos.some((b) => b.activo && b.id === this.datos.reserva.barbero.id)
        ? this.datos.reserva.barbero.id
        : null,
      Validators.required,
    ),
    inicio: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    motivo: new FormControl('', {
      nonNullable: true,
      validators: [motivoObligatorio, Validators.maxLength(300)],
    }),
  });
  readonly franjas = signal<FranjaDto[]>([]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  constructor() {
    this.cargar();
  }
  cargar() {
    if (this.guardando()) return;
    this.peticion?.unsubscribe();
    this.formulario.controls.inicio.setValue('');
    this.franjas.set([]);
    this.mensaje.set('');
    const { fecha, barberoId } = this.formulario.getRawValue();
    if (!fecha || !barberoId) return;
    this.cargando.set(true);
    this.peticion = this.disponibilidad
      .consultar({
        servicioId: this.datos.reserva.servicio.id,
        fecha,
        barberoId,
        excluirReservaId: this.datos.reserva.id,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (datos) => this.franjas.set(datos.franjas),
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
  confirmar() {
    if (this.guardando() || this.cargando()) return;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    const { inicio, barberoId, motivo } = this.formulario.getRawValue();
    if (!this.franjas().some((f) => f.inicio === inicio && f.barberoIds.includes(barberoId!)))
      return;
    this.guardando.set(true);
    this.referencia.disableClose = true;
    this.mensaje.set('');
    this.formulario.disable({ emitEvent: false });
    this.api
      .reprogramar(this.datos.reserva.id, {
        inicio,
        barberoId: barberoId!,
        motivo: motivo.trim(),
        version: this.datos.reserva.version,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.referencia.disableClose = false;
          this.formulario.enable({ emitEvent: false });
        }),
      )
      .subscribe({
        next: () => this.referencia.close('guardada'),
        error: (error: HttpErrorResponse) => {
          if (error.status === 409) this.referencia.close('actualizada');
          else this.mensaje.set(mostrarErrores(this.formulario, error));
        },
      });
  }
}
