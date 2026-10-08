import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Placeholders decorativos con un único anuncio de carga para lectores de pantalla. */
@Component({
  selector: 'app-reserva-esqueleto',
  template: `<span class="oculto" role="status">{{ texto() }}</span>
    <div class="esqueletos" [class.franjas]="franjas()" aria-hidden="true">
      @for (item of piezas; track item) {
        <span></span>
      }
    </div>`,
  styleUrl: './reserva-esqueleto.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaEsqueleto {
  readonly texto = input.required<string>();
  readonly franjas = input(false);
  readonly piezas = [1, 2, 3, 4, 5, 6];
}
