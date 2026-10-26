import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { FechaLimaPipe } from '../core/tiempo/fecha-lima-pipe';
import { EstadoContenido } from './estado-contenido';

/** El padre conserva el enlace solo hasta descartarlo o destruir la vista. */
@Component({
  selector: 'app-enlace-invitacion',
  imports: [MatButtonModule, FechaLimaPipe, EstadoContenido],
  template: `<section aria-label="Enlace de invitación">
    <p class="enlace">{{ enlaceCompleto() }}</p>
    <button mat-stroked-button (click)="copiar()">Copiar</button>
    <span role="status">{{ mensaje() }}</span>
    <app-estado-contenido
      tipo="aviso"
      [texto]="
        expiraEn()
          ? 'El enlace se muestra una sola vez y caduca el ' +
            (expiraEn()! | fechaLima) +
            ' (hora de Lima).'
          : 'El enlace se muestra una sola vez.'
      "
    />
  </section>`,
  styles: `
    section {
      margin-block: 16px;
    }
    .enlace {
      font-family: monospace;
      overflow-wrap: anywhere;
      padding: 16px;
      border-radius: 12px;
      background: var(--color-fondo);
    }
    span {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnlaceInvitacion {
  readonly enlace = input.required<string>();
  readonly expiraEn = input<string | null>(null);
  readonly mensaje = signal('');
  readonly enlaceCompleto = () =>
    new URL(this.enlace().replace(/^\/api\/invitaciones\//, '/invitacion/'), location.origin).href;
  async copiar() {
    try {
      await navigator.clipboard.writeText(this.enlaceCompleto());
      this.mensaje.set('Enlace copiado.');
    } catch {
      this.mensaje.set('No se pudo copiar. Seleccione el enlace y cópielo.');
    }
  }
}
