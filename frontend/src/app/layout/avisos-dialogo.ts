import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { AvisosPanel } from '../features/mis-citas/avisos-panel';

@Component({
  selector: 'app-avisos-dialogo',
  imports: [MatButtonModule, MatDialogModule, AvisosPanel],
  template: `
    <h2 mat-dialog-title>Avisos</h2>
    <mat-dialog-content><app-avisos-panel [mostrarEncabezado]="false" /></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: ['mat-dialog-content { padding: 0; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvisosDialogo {}
