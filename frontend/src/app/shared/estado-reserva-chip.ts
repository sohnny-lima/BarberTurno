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
        border: 1px solid currentColor;
      }
      [data-estado='PENDIENTE'] {
        color: #624000;
        background: #fff3ce;
      }
      [data-estado='CONFIRMADA'] {
        color: #124e65;
        background: #e1f2fa;
      }
      [data-estado='EN_ATENCION'] {
        color: #4f2875;
        background: #f1e7ff;
      }
      [data-estado='COMPLETADA'] {
        color: #20532a;
        background: #e7f5e8;
      }
      [data-estado='NO_ASISTIO'] {
        color: #812b22;
        background: #ffece8;
      }
      [data-estado='CANCELADA'] {
        color: #414950;
        background: #eef0f2;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstadoReservaChip {
  readonly estado = input.required<EstadoReserva>();
  readonly nombres = ESTADOS_RESERVA;
}
