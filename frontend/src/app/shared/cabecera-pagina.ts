import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
} from '@angular/core';
import { TituloPagina } from './titulo-pagina';

/** Un único h1 accesible; la barra móvil puede repetirlo solo como texto visual. */
@Component({
  selector: 'app-cabecera-pagina',
  template: `<header [class.titulo-movil]="ocultarEnMovil()">
    <div>
      <h1>{{ titulo() }}</h1>
      @if (subtitulo()) {
        <p>{{ subtitulo() }}</p>
      }
    </div>
    <div class="accion"><ng-content /></div>
  </header>`,
  styleUrl: './cabecera-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CabeceraPagina {
  readonly titulo = input.required<string>();
  readonly subtitulo = input('');
  readonly ocultarEnMovil = input(true);
  constructor() {
    const barra = inject(TituloPagina);
    effect(() => barra.texto.set(this.titulo()));
    inject(DestroyRef).onDestroy(() => {
      if (barra.texto() === this.titulo()) barra.texto.set(null);
    });
  }
}
