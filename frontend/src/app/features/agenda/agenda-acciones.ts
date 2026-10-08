import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { EstadoTransicion, ReservaDto } from '../../core/modelos/reservas';
import { ACCIONES_TRANSICION } from './transicion-dialogo';

/** Distribuye los permisos recibidos entre la acción destacada y el menú Material. */
@Component({
  selector: 'app-agenda-acciones',
  imports: [MatButtonModule, MatMenuModule],
  templateUrl: './agenda-acciones.html',
  styleUrl: './agenda-acciones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgendaAcciones {
  readonly reserva = input.required<ReservaDto>();
  readonly admin = input(false);
  readonly escritorio = input(false);
  readonly semana = input(false);
  readonly barberosDisponibles = input(false);
  readonly transicion = output<EstadoTransicion>();
  readonly cancelar = output<void>();
  readonly reprogramar = output<void>();
  readonly cambios = output<void>();
  readonly acciones = Object.entries(ACCIONES_TRANSICION) as [EstadoTransicion, string][];
  readonly principal = computed(() => {
    const primera = this.acciones.find(([estado]) =>
      this.reserva().permisos.transiciones.includes(estado),
    );
    return primera?.[0] !== 'NO_ASISTIO' && (!this.semana() || this.escritorio())
      ? primera
      : undefined;
  });
  readonly reprogramarVisible = computed(
    () =>
      this.escritorio() && !this.semana() && this.admin() && this.reserva().permisos.reprogramar,
  );
}
