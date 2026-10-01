import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { SesionService } from '../../core/auth/sesion-service';
import { confirmarPassword, errorCampo, mostrarErrores } from '../../shared/formulario';

@Component({
  selector: 'app-registro',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    RouterLink,
  ],
  templateUrl: './registro.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Registro {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly errorCampo = errorCampo;
  readonly formulario = new FormGroup(
    {
      nombre: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(100)],
      }),
      correo: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.email, Validators.maxLength(254)],
      }),
      telefono: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern('[0-9]{9}')],
      }),
      password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      confirmacion: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      aceptaPrivacidad: new FormControl(false, {
        nonNullable: true,
        validators: [Validators.requiredTrue],
      }),
    },
    { validators: confirmarPassword },
  );
  enviar() {
    if (this.cargando()) return;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    const { nombre, correo, telefono, password, aceptaPrivacidad } = this.formulario.getRawValue();
    const datos = { nombre, correo, telefono, password, aceptaPrivacidad };
    this.cargando.set(true);
    this.mensaje.set('');
    this.sesion
      .registrar(datos)
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
