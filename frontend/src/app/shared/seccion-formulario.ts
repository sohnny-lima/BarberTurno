import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Tarjeta de formulario con título de sección opcional y contenido proyectado. */
@Component({
  selector: 'app-seccion-formulario',
  template: `<section>
    @if (titulo()) {
      <h2>{{ titulo() }}</h2>
    }
    <ng-content />
  </section>`,
  styleUrl: './seccion-formulario.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SeccionFormulario {
  readonly titulo = input('');
}
