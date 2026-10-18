import { BreakpointObserver } from '@angular/cdk/layout';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, TemplateRef } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

/** Instancia solo una disposición, con el mismo corte de 1200 px que la Agenda. */
@Component({
  selector: 'app-lista-adaptable',
  imports: [NgTemplateOutlet],
  template: `@if (escritorio()) {
      <div class="tabla"><ng-container [ngTemplateOutlet]="tabla()" /></div>
    } @else {
      <div class="tarjetas" role="list" [attr.aria-label]="nombre()">
        <ng-container [ngTemplateOutlet]="tarjetas()" />
      </div>
    }`,
  styleUrl: './lista-adaptable.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaAdaptable {
  readonly nombre = input.required<string>();
  readonly tabla = input.required<TemplateRef<unknown>>();
  readonly tarjetas = input.required<TemplateRef<unknown>>();
  readonly escritorio = toSignal(
    inject(BreakpointObserver)
      .observe('(min-width: 1200px)')
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );
}
