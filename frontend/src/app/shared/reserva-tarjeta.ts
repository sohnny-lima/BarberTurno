import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { ReservaDto } from '../core/modelos/reservas';
import { FechaLimaPipe } from '../core/tiempo/fecha-lima-pipe';
import { EstadoReservaChip } from './estado-reserva-chip';

@Component({
  selector: 'app-reserva-tarjeta',
  imports: [CurrencyPipe, FechaLimaPipe, MatButtonModule, RouterLink, EstadoReservaChip],
  templateUrl: './reserva-tarjeta.html',
  styles: [':host { display: block; } h2 { overflow-wrap: anywhere; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaTarjeta {
  readonly reserva = input.required<ReservaDto>();
  readonly ahora = input(Date.now());
  readonly cancelar = output<ReservaDto>();
  // Solo decide si mostrar una explicación. Las acciones obedecen siempre al DTO.
  readonly mostrarAyuda = computed(() => {
    const r = this.reserva();
    return (
      !r.permisos.reprogramar &&
      !r.permisos.cancelar &&
      ['PENDIENTE', 'CONFIRMADA'].includes(r.estado) &&
      Date.parse(r.inicio) > this.ahora()
    );
  });
}
