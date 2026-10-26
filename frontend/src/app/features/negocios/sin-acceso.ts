import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { EstadoContenido } from '../../shared/estado-contenido';

@Component({
  selector: 'app-sin-acceso',
  imports: [RouterLink, MatButtonModule, EstadoContenido],
  template: `<app-estado-contenido tipo="aviso" texto="No tiene acceso a esta sección."
    ><a mat-button routerLink="/negocios">Ir a mis barberías</a></app-estado-contenido
  >`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SinAcceso {}
