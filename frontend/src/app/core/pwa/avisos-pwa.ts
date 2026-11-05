import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Red } from './red';
import { Actualizaciones } from './actualizaciones';

@Component({
  selector: 'app-avisos-pwa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="avisos" role="status" aria-live="polite">
      @if (red.sinConexion()) {
        <p>
          Sin conexión. Lo que ve puede no estar actualizado; no se guardará ningún cambio hasta que
          vuelva la conexión.
        </p>
      } @else if (red.recuperada()) {
        <p>Conexión restablecida. Puede volver a consultar o repetir su acción.</p>
      }
      @if (version.disponible() || version.irrecuperable()) {
        <p>
          {{
            version.irrecuperable()
              ? 'Es necesario recargar la aplicación para continuar.'
              : 'Hay una versión nueva.'
          }}
          <button type="button" [disabled]="version.activando()" (click)="version.actualizar()">
            {{ version.irrecuperable() ? 'Recargar' : 'Actualizar' }}
          </button>
        </p>
      }
      @if (version.error()) {
        <p>{{ version.error() }}</p>
      }
    </div>
  `,
  styles: `
    .avisos {
      position: fixed;
      inset: auto 0 env(safe-area-inset-bottom, 0);
      z-index: 1100;
      pointer-events: none;
    }
    p {
      margin: 0;
      padding: 0.75rem 1rem;
      background: #fff3cd;
      color: #352600;
      border: 1px solid #8a6d1d;
      pointer-events: auto;
    }
    button {
      min-height: 44px;
      padding: 0.5rem 1rem;
      margin-inline-start: 0.5rem;
      background: #145c43;
      color: white;
      border: 0;
      border-radius: 0.5rem;
      cursor: pointer;
    }
    button:focus-visible {
      outline: 3px solid #352600;
      outline-offset: 2px;
    }
  `,
})
export class AvisosPwa {
  readonly red = inject(Red);
  readonly version = inject(Actualizaciones);
}
