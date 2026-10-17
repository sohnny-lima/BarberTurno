import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Barras nativas con el mismo nombre accesible que el desglose original. */
@Component({
  selector: 'app-reporte-barras',
  template: `<section [attr.aria-labelledby]="seccion()">
    <h2 [id]="seccion()">{{ titulo() }}</h2>
    @for (grupo of grupos(); track grupo.id) {
      <div class="grupo">
        <label [for]="prefijo() + grupo.id"
          ><span>{{ grupo.nombre }}:{{ ' ' }}</span>
          <span>{{ grupo.total }} de {{ total() }}</span></label
        >
        <progress [id]="prefijo() + grupo.id" [max]="total() || 1" [value]="grupo.total">
          {{ grupo.total }}
        </progress>
      </div>
    } @empty {
      <p>{{ vacio() }}</p>
    }
  </section>`,
  styleUrl: './reporte-barras.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReporteBarras {
  readonly seccion = input.required<string>();
  readonly titulo = input.required<string>();
  readonly prefijo = input.required<string>();
  readonly vacio = input.required<string>();
  readonly total = input.required<number>();
  readonly grupos = input.required<{ id: number; nombre: string; total: number }[]>();
}
