import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { fechaPresentacion } from '../../shared/fecha-presentacion';
import { ReservaStore } from './reserva.store';

/** Resumen lateral de los pasos previos a confirmar, sin reglas propias. */
@Component({
  selector: 'app-reserva-resumen',
  imports: [MatButtonModule, FechaLimaPipe],
  templateUrl: './reserva-resumen.html',
  styleUrl: './reserva-resumen.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaResumen {
  readonly store = inject(ReservaStore);
  readonly avanzar = output<void>();
  readonly fechaLegible = fechaPresentacion;
  readonly nombreProfesional = () =>
    this.store.preferencia() === null
      ? 'Sin preferencia'
      : this.store.barberos().find((b) => b.id === this.store.preferencia())?.nombre;
}
