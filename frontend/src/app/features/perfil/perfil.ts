import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { PerfilApi } from '../../core/api/perfil-api';
import { SesionService } from '../../core/auth/sesion-service';
import { PerfilDto } from '../../core/modelos/identidad';
import { errorCampo, mostrarErrores } from '../../shared/formulario';

@Component({
  selector: 'app-perfil',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink],
  templateUrl: './perfil.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Perfil {
  private readonly api = inject(PerfilApi);
  private readonly sesion = inject(SesionService);
  private readonly destroyRef = inject(DestroyRef);
  readonly cargando = signal(false);
  readonly disponible = signal(false);
  readonly mensaje = signal('');
  readonly errorCampo = errorCampo;
  readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    telefono: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern('[0-9]{9}')],
    }),
    correo: new FormControl('', { nonNullable: true }),
  });
  constructor() {
    this.cargar();
  }
  cargar() {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.mensaje.set('');
    this.api
      .obtener()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (perfil) => {
          this.rellenar(perfil);
          this.disponible.set(true);
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
  enviar() {
    if (this.cargando() || !this.disponible()) return;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    const { nombre, telefono } = this.formulario.getRawValue();
    this.cargando.set(true);
    this.mensaje.set('');
    this.api
      .actualizar({ nombre, telefono: telefono || null })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (perfil) => {
          this.rellenar(perfil);
          this.sesion.actualizarPerfil(perfil);
          this.mensaje.set('Perfil guardado.');
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
  private rellenar(perfil: PerfilDto) {
    this.formulario.setValue({
      nombre: perfil.nombre,
      telefono: perfil.telefono ?? '',
      correo: perfil.correo,
    });
  }
}
