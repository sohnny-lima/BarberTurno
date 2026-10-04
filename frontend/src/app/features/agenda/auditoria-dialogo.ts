import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { finalize } from 'rxjs';
import { ReservasApi } from '../../core/api/reservas-api';
import { AuditoriaDto, ReservaDto } from '../../core/modelos/reservas';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { ESTADOS_RESERVA } from '../../shared/estado-reserva-chip';
import { mostrarErrores } from '../../shared/formulario';

@Component({
  selector: 'app-auditoria-dialogo',
  imports: [MatButtonModule, MatDialogModule, FechaLimaPipe],
  templateUrl: './auditoria-dialogo.html',
  styleUrl: './auditoria-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditoriaDialogo {
  readonly reserva = inject<ReservaDto>(MAT_DIALOG_DATA);
  private readonly api = inject(ReservasApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly filas = signal<AuditoriaDto[]>([]);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly estados = ESTADOS_RESERVA;
  readonly acciones: Record<AuditoriaDto['accion'], string> = {
    CREAR: 'Crear',
    CONFIRMAR: 'Confirmar',
    INICIAR: 'Iniciar atención',
    COMPLETAR: 'Completar',
    NO_ASISTIO: 'No asistió',
    CANCELAR: 'Cancelar',
    REPROGRAMAR: 'Reprogramar',
  };
  constructor() {
    this.cargar();
  }
  cargar() {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.mensaje.set('');
    this.api
      .auditoria(this.reserva.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (datos) => this.filas.set(datos),
        error: (error) => this.mensaje.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
}
