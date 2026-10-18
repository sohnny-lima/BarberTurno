import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReservaDto } from '../../core/modelos/reservas';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { EstadoReservaChip } from '../../shared/estado-reserva-chip';
import { ListaAdaptable } from '../../shared/lista-adaptable';

/** Conserva los seis datos del historial en una sola disposición a la vez. */
@Component({
  selector: 'app-reporte-historial',
  imports: [FechaLimaPipe, EstadoReservaChip, ListaAdaptable],
  templateUrl: './reporte-historial.html',
  styleUrl: './reporte-historial.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReporteHistorial {
  readonly filas = input.required<ReservaDto[]>();
}
