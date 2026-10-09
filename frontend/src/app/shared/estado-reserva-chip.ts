import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EstadoReserva } from '../core/modelos/reservas';

export const ESTADOS_RESERVA: Record<EstadoReserva, string> = {
  PENDIENTE: 'Pendiente',
  CONFIRMADA: 'Confirmada',
  EN_ATENCION: 'En atención',
  COMPLETADA: 'Completada',
  NO_ASISTIO: 'No asistió',
  CANCELADA: 'Cancelada',
};
@Component({
  selector: 'app-estado-reserva-chip',
  template: '<span class="chip" [attr.data-estado]="estado()">{{ nombres[estado()] }}</span>',
  styles: [
    `
      .chip {
        display: inline-block;
        border-radius: 20px;
        padding: 6px 12px;
        font-weight: 700;
        font-size: 13px;
        line-height: 1.5;
      }
      [data-estado='PENDIENTE'] {
        color: var(--color-pendiente);
        background: var(--color-pendiente-fondo);
      }
      [data-estado='CONFIRMADA'] {
        color: var(--color-confirmada);
        background: var(--color-confirmada-fondo);
      }
      [data-estado='EN_ATENCION'] {
        color: var(--color-en-atencion);
        background: var(--color-en-atencion-fondo);
      }
      [data-estado='COMPLETADA'] {
        color: var(--color-completada);
        background: var(--color-completada-fondo);
      }
      [data-estado='NO_ASISTIO'] {
        color: var(--color-no-asistio);
        background: var(--color-no-asistio-fondo);
      }
      [data-estado='CANCELADA'] {
        color: var(--color-cancelada);
        background: var(--color-cancelada-fondo);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstadoReservaChip {
  readonly estado = input.required<EstadoReserva>();
  readonly nombres = ESTADOS_RESERVA;
}
