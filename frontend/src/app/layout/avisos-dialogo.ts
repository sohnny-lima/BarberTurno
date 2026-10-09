import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { AvisosPanel } from '../features/mis-citas/avisos-panel';
import { AvisosService } from '../core/notificaciones/avisos-service';

@Component({
  selector: 'app-avisos-dialogo',
  imports: [MatButtonModule, MatDialogModule, AvisosPanel],
  template: `
    <h2 mat-dialog-title id="titulo-dialogo-avisos">
      <span>Avisos</span
      ><span class="insignia" aria-hidden="true">{{ avisos.noLeidas() }} sin leer</span>
    </h2>
    <mat-dialog-content
      ><app-avisos-panel [mostrarEncabezado]="false" [incrustado]="true"
    /></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: [
    'h2 { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 20px; } h2::before { display: none; } .insignia { font: 600 13px/18px var(--font-cuerpo); background: var(--color-verde-claro); color: var(--color-verde); border-radius: 30px; padding: 4px 10px; } mat-dialog-actions { border-top: 1px solid var(--color-linea); }',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvisosDialogo {
  readonly avisos = inject(AvisosService);
}
