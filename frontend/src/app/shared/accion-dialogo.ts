import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/** La acción de riesgo se declara en los datos de presentación, nunca se infiere del texto. */
@Component({
  selector: 'app-accion-dialogo',
  imports: [MatButtonModule],
  template: `<button
    [matButton]="riesgo() ? 'outlined' : 'filled'"
    [class.riesgo]="riesgo()"
    [disabled]="desactivada()"
    type="button"
    (click)="confirmar.emit()"
  >
    <ng-content />
  </button>`,
  styleUrl: './accion-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccionDialogo {
  readonly riesgo = input(false);
  readonly desactivada = input(false);
  readonly confirmar = output<void>();
}
