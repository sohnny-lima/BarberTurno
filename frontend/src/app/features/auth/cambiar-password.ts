import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { SesionService } from '../../core/auth/sesion-service';
import { errorCampo, mostrarErrores } from '../../shared/formulario';

@Component({
  selector: 'app-cambiar-password',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './cambiar-password.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CambiarPassword {
  readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly errorCampo = errorCampo;
  readonly formulario = new FormGroup({
    passwordActual: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    passwordNueva: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  enviar() {
    if (this.cargando()) return;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    this.cargando.set(true);
    this.mensaje.set('');
    this.sesion
      .cambiarPassword(this.formulario.getRawValue())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: () => {
          this.formulario.reset();
          void this.router.navigateByUrl(this.sesion.inicio());
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
}
