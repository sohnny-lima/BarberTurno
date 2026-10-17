import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/** Estados de presentación; el consumidor conserva sus textos y decide cuándo mostrarlos. */
@Component({
  selector: 'app-estado-contenido',
  imports: [MatButtonModule],
  template: `@if (tipo() === 'carga') {
      <span class="oculto" role="status">{{ texto() }}</span>
      <div class="esqueletos" [class.formulario]="forma() === 'formulario'" aria-hidden="true">
        @for (pieza of piezas; track pieza) {
          <span></span>
        }
      </div>
    } @else {
      <div [class]="tipo()" [attr.role]="tipo() === 'error' ? 'alert' : 'status'">
        <p>{{ texto() }}</p>
        @if (reintento()) {
          <button mat-button type="button" (click)="reintentar.emit()">{{ reintento() }}</button>
        }
        <ng-content />
      </div>
    }`,
  styleUrl: './estado-contenido.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstadoContenido {
  readonly tipo = input.required<'carga' | 'error' | 'vacio' | 'aviso'>();
  readonly texto = input.required<string>();
  readonly forma = input<'lista' | 'formulario'>('lista');
  readonly reintento = input('');
  readonly reintentar = output<void>();
  readonly piezas = [1, 2, 3];
}
