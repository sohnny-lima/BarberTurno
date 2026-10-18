import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { errorCampo } from '../../shared/formulario';
import { intervalo } from './editor-semana';

/** Presenta los controles existentes de un día sin modificar las reglas del editor. */
@Component({
  selector: 'app-dia-semana',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './dia-semana.html',
  styleUrl: './dia-semana.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiaSemana {
  readonly dia = input.required<string>();
  readonly filas = input.required<ReturnType<typeof intervalo>[]>();
  readonly quitar = output<ReturnType<typeof intervalo>>();
  readonly agregar = output<void>();
  readonly errorCampo = errorCampo;
}
