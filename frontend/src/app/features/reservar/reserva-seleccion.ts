import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ReservaStore } from './reserva.store';

/** Recuerda la selección vigente antes de elegir el horario en la vista móvil. */
@Component({
  selector: 'app-reserva-seleccion',
  imports: [MatButtonModule],
  template: `@if (store.servicio(); as servicio) {
    <section aria-label="Servicio y profesional elegidos">
      <span class="icono" aria-hidden="true">✂</span>
      <div>
        <strong>{{ servicio.nombre }}</strong>
        <span>{{ servicio.duracionMin }} min · S/ {{ servicio.precio.toFixed(2) }}</span>
        <span>Profesional: {{ profesional() }}</span>
      </div>
      <button mat-button type="button" (click)="store.irPaso(0)" [disabled]="store.enviando()">
        Cambiar
      </button>
    </section>
  }`,
  styleUrl: './reserva-seleccion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaSeleccion {
  readonly store = inject(ReservaStore);
  readonly profesional = computed(() =>
    this.store.preferencia() === null
      ? 'Sin preferencia'
      : this.store.barberos().find((barbero) => barbero.id === this.store.preferencia())?.nombre,
  );
}
