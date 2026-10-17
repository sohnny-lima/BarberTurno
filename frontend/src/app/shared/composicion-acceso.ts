import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Fotografía decorativa de la sede junto al formulario desde 1024 px. */
@Component({
  selector: 'app-composicion-acceso',
  template: `<div class="composicion" [class.registro]="registro()">
    <div class="columna"><ng-content /></div>
    <img src="fotos/interior-vacio.webp" alt="" />
  </div>`,
  styleUrl: './composicion-acceso.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComposicionAcceso {
  readonly registro = input(false);
}
