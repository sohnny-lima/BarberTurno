import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { NegocioPropioDto, NOMBRES_PAPEL, Papel } from '../../core/modelos/negocios';

@Component({
  selector: 'app-papel-dialogo',
  imports: [MatDialogModule, MatButtonModule, MatRadioModule],
  template: `<h2 mat-dialog-title>¿Cómo quiere entrar en {{ negocio.nombre }}?</h2>
    <mat-dialog-content
      ><mat-radio-group
        aria-label="Papel en la barbería"
        [value]="papel()"
        (change)="papel.set($event.value)"
      >
        <mat-radio-button value="PERSONAL"
          >Como personal ({{ nombres[negocio.rol] }})<small
            >Agenda y gestión según sus permisos.</small
          ></mat-radio-button
        >
        <mat-radio-button value="CLIENTE"
          >Como cliente<small>Reservar y ver sus citas.</small></mat-radio-button
        >
      </mat-radio-group></mat-dialog-content
    >
    <mat-dialog-actions align="end"
      ><button mat-button mat-dialog-close>Cancelar</button
      ><button mat-flat-button [mat-dialog-close]="papel()">Entrar</button></mat-dialog-actions
    >`,
  styles: `
    mat-radio-group {
      display: grid;
      gap: 12px;
    }
    mat-radio-button {
      min-height: 56px;
    }
    small {
      display: block;
      color: var(--color-texto-2);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PapelDialogo {
  readonly negocio = inject<NegocioPropioDto>(MAT_DIALOG_DATA);
  readonly nombres = NOMBRES_PAPEL;
  readonly papel = signal<Papel>('PERSONAL');
}
