import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { finalize, Observable } from 'rxjs';
import { mostrarErrores } from './formulario';

export interface ConfirmarEstadoDatos {
  nombre: string;
  titulo?: string;
  texto?: string;
  boton?: string;
  activar: boolean;
  cambiar: () => Observable<number>;
}
@Component({
  selector: 'app-confirmar-estado-dialogo',
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './confirmar-estado-dialogo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmarEstadoDialogo {
  readonly datos = inject<ConfirmarEstadoDatos>(MAT_DIALOG_DATA);
  readonly referencia = inject(MatDialogRef<ConfirmarEstadoDialogo, boolean>);
  private readonly destroyRef = inject(DestroyRef);
  readonly guardando = signal(false);
  readonly completado = signal(false);
  readonly aviso = signal('');
  readonly error = signal('');
  confirmar() {
    if (this.guardando() || this.completado()) return;
    this.guardando.set(true);
    this.referencia.disableClose = true;
    this.error.set('');
    this.datos
      .cambiar()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.referencia.disableClose = this.completado() && !!this.aviso();
        }),
      )
      .subscribe({
        next: (reservas) => {
          this.completado.set(true);
          if (!this.datos.activar && reservas > 0) {
            this.aviso.set('Tiene ' + reservas + ' reservas futuras: gestiónelas en la agenda');
          } else {
            this.referencia.close(true);
          }
        },
        error: (error) => this.error.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
}
