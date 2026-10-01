import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-proximamente',
  template: '<section class="tarjeta"><h1>{{ titulo }}</h1><p>Próximamente</p></section>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Proximamente {
  readonly titulo = inject(ActivatedRoute).snapshot.data['titulo'] as string;
}
