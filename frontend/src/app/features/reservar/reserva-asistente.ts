import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';

/** Ajusta la distribución del stepper con estilos diferidos acotados a este contenedor. */
@Component({
  selector: 'app-reserva-asistente',
  template: '<ng-content />',
  styleUrl: './reserva-asistente.scss',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaAsistente {}
