import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { EstadoTransicion, ReservaDto } from '../../core/modelos/reservas';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { EstadoReservaChip } from '../../shared/estado-reserva-chip';
import { AgendaAcciones } from './agenda-acciones';

/** Una misma cita en columnas de escritorio o tarjeta diaria/semanal. */
@Component({
  selector: 'app-agenda-cita',
  imports: [FechaLimaPipe, EstadoReservaChip, AgendaAcciones],
  templateUrl: './agenda-cita.html',
  styleUrl: './agenda-cita.scss',
  host: {
    '[class.escritorio]': 'escritorio()',
    '[class.semana]': 'semana() && !escritorio()',
    '[attr.role]': 'escritorio() ? "row" : "article"',
    '[attr.aria-label]': 'reserva().codigo',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgendaCita {
  readonly reserva = input.required<ReservaDto>();
  readonly escritorio = input(false);
  readonly semana = input(false);
  readonly admin = input(false);
  readonly todos = input(false);
  readonly barberosDisponibles = input(false);
  readonly transicion = output<EstadoTransicion>();
  readonly cancelar = output<void>();
  readonly reprogramar = output<void>();
  readonly cambios = output<void>();
  telefono(numero: string) {
    return /^\d{9}$/.test(numero) ? numero.replace(/(\d{3})(\d{3})(\d{3})/, '$1 $2 $3') : numero;
  }
}
