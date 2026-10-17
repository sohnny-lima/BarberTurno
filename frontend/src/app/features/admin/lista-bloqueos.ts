import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { BloqueoDto } from '../../core/modelos/horarios';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';

/** Lista de presentación de bloqueos; la página conserva la confirmación y la petición. */
@Component({
  selector: 'app-lista-bloqueos',
  imports: [MatButtonModule, FechaLimaPipe],
  templateUrl: './lista-bloqueos.html',
  styleUrl: './lista-bloqueos.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaBloqueos {
  readonly bloqueos = input.required<BloqueoDto[]>();
  readonly eliminar = output<BloqueoDto>();
}
