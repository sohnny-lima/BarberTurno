import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EstadoReserva } from '../../core/modelos/reservas';
import { EstadoReservaChip } from '../../shared/estado-reserva-chip';

/** Presenta el total y las seis categorías recibidas, sin recalcular el reporte. */
@Component({
  selector: 'app-reporte-cifras',
  imports: [EstadoReservaChip],
  template: `<section aria-label="Reservas por estado" class="estadisticas">
    <article class="estadistica total">
      <span>Total</span><strong>{{ total() }}</strong>
    </article>
    @for (tarjeta of tarjetas(); track tarjeta.estado) {
      <article class="estadistica">
        <app-estado-reserva-chip [estado]="tarjeta.estado" />
        <strong>{{ tarjeta.total }}</strong>
      </article>
    }
  </section>`,
  styleUrl: './reporte-cifras.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReporteCifras {
  readonly total = input.required<number>();
  readonly tarjetas = input.required<{ estado: EstadoReserva; total: number }[]>();
}
