import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

@Component({
  selector: 'app-password-temporal-dialogo',
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './password-temporal-dialogo.html',
  styles: '.password { font-family: monospace; font-size: 1.25rem; overflow-wrap: anywhere; }',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordTemporalDialogo {
  readonly password = inject<string>(MAT_DIALOG_DATA);
  readonly mensaje = signal('');
  async copiar() {
    try {
      await navigator.clipboard.writeText(this.password);
      this.mensaje.set('Contraseña copiada.');
    } catch {
      this.mensaje.set('No pudimos copiarla. Comuníquela en persona desde este diálogo.');
    }
  }
}
