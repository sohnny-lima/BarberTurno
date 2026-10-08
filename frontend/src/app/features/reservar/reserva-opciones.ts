import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ReservaStore } from './reserva.store';

/** Opciones visuales; el store conserva las selecciones y las consultas existentes. */
@Component({
  selector: 'app-reserva-opciones',
  templateUrl: './reserva-opciones.html',
  styleUrl: './reserva-opciones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaOpciones {
  readonly store = inject(ReservaStore);
  iniciales(nombre: string) {
    return nombre
      .split(' ')
      .map((parte) => parte[0])
      .slice(0, 2)
      .join('');
  }
}
