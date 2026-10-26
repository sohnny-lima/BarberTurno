import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { PlataformaApi } from '../../core/api/plataforma-api';
import { NegocioCreadoDto } from '../../core/modelos/plataforma';
import { errorCampo, mostrarErrores } from '../../shared/formulario';

@Component({
  selector: 'app-alta-negocio',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `<h2 mat-dialog-title>Nueva barbería</h2>
    <form [formGroup]="formulario" (ngSubmit)="crear()">
      <mat-dialog-content class="formulario">
        <mat-form-field
          ><mat-label>Nombre</mat-label
          ><input matInput formControlName="nombre" maxlength="100" /><mat-error>{{
            errorCampo(formulario.controls.nombre)
          }}</mat-error></mat-form-field
        >
        <mat-form-field
          ><mat-label>Dirección web</mat-label><span matTextPrefix>/n/</span
          ><input matInput formControlName="slug" maxlength="40" autocapitalize="none" /><mat-hint
            >Letras minúsculas, números y guiones simples. Hasta 40 caracteres.</mat-hint
          ><mat-error>{{
            formulario.controls.slug.hasError('pattern')
              ? 'Use letras minúsculas, números y guiones simples.'
              : errorCampo(formulario.controls.slug)
          }}</mat-error></mat-form-field
        >
        <mat-form-field
          ><mat-label>Correo del propietario</mat-label
          ><input
            matInput
            formControlName="correoPropietario"
            type="email"
            autocomplete="email"
          /><mat-error>{{
            errorCampo(formulario.controls.correoPropietario)
          }}</mat-error></mat-form-field
        >
        @if (mensaje()) {
          <p role="alert">{{ mensaje() }}</p>
        }</mat-dialog-content
      ><mat-dialog-actions align="end"
        ><button mat-button type="button" [disabled]="guardando()" (click)="referencia.close()">
          Cancelar</button
        ><button mat-flat-button type="submit" [disabled]="guardando()">
          {{ guardando() ? 'Creando…' : 'Crear barbería' }}
        </button></mat-dialog-actions
      >
    </form>`,
  styleUrl: '../admin/catalogo-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AltaNegocio {
  private readonly api = inject(PlataformaApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly referencia = inject(MatDialogRef<AltaNegocio, NegocioCreadoDto>);
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  readonly errorCampo = errorCampo;
  readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    slug: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(40),
        Validators.pattern('[a-z0-9](-?[a-z0-9])*'),
      ],
    }),
    correoPropietario: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
  });
  crear() {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.guardando()) return;
    this.guardando.set(true);
    this.referencia.disableClose = true;
    this.mensaje.set('');
    this.api
      .crear(this.formulario.getRawValue())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.referencia.disableClose = false;
        }),
      )
      .subscribe({
        next: (creado) => this.referencia.close(creado),
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
}
