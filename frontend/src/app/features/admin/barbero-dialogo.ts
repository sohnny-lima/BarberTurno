import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, take } from 'rxjs';
import { BarberosApi } from '../../core/api/barberos-api';
import { BarberoDto, CrearBarberoDto } from '../../core/modelos/catalogo';
import { errorCampo, mostrarErrores } from '../../shared/formulario';
import { PasswordTemporalDialogo } from './password-temporal-dialogo';
import {
  generarPasswordTemporal,
  nombreSinEspacios,
  passwordPersonal,
} from './validadores-catalogo';

@Component({
  selector: 'app-barbero-dialogo',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './barbero-dialogo.html',
  styleUrl: './catalogo-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarberoDialogo {
  readonly barbero = inject<BarberoDto | null>(MAT_DIALOG_DATA);
  readonly referencia = inject(MatDialogRef<BarberoDialogo, boolean>);
  private readonly api = inject(BarberosApi);
  private readonly dialogos = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  readonly errorCampo = errorCampo;
  readonly formulario = new FormGroup({
    modo: new FormControl<'nueva' | 'vincular'>('nueva', { nonNullable: true }),
    nombre: new FormControl(this.barbero?.nombre ?? '', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(2),
        Validators.maxLength(100),
        nombreSinEspacios,
      ],
    }),
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    telefono: new FormControl(this.barbero?.telefono ?? '', {
      nonNullable: true,
      validators: [Validators.pattern('[0-9]{9}')],
    }),
    especialidad: new FormControl(this.barbero?.especialidad ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    passwordTemporal: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, passwordPersonal],
    }),
    usuarioId: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)],
    }),
  });
  constructor() {
    this.ajustarModo();
  }
  ajustarModo() {
    const vincular = !this.barbero && this.formulario.controls.modo.value === 'vincular';
    const controles = this.formulario.controls;
    for (const control of [controles.nombre, controles.telefono]) {
      if (vincular) control.disable();
      else control.enable();
    }
    for (const control of [controles.correo, controles.passwordTemporal]) {
      if (vincular || this.barbero) control.disable();
      else control.enable();
    }
    if (vincular) controles.usuarioId.enable();
    else controles.usuarioId.disable();
    controles.passwordTemporal.reset('');
    this.mensaje.set('');
  }
  generar() {
    this.formulario.controls.passwordTemporal.setValue(generarPasswordTemporal());
  }
  guardar() {
    if (this.guardando()) return;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    const datos = this.formulario.getRawValue();
    const nueva = !this.barbero && datos.modo === 'nueva';
    const alta: CrearBarberoDto =
      datos.modo === 'vincular'
        ? { usuarioId: datos.usuarioId!, especialidad: datos.especialidad }
        : {
            nombre: datos.nombre,
            correo: datos.correo,
            telefono: datos.telefono || null,
            especialidad: datos.especialidad,
            passwordTemporal: datos.passwordTemporal,
          };
    const peticion = this.barbero
      ? this.api.editar(this.barbero.id, {
          nombre: datos.nombre,
          telefono: datos.telefono || null,
          especialidad: datos.especialidad,
        })
      : this.api.crear(alta);
    this.formulario.controls.modo.disable();
    this.guardando.set(true);
    this.referencia.disableClose = true;
    this.mensaje.set('');
    peticion
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.formulario.controls.modo.enable();
          this.referencia.disableClose = false;
        }),
      )
      .subscribe({
        next: () => {
          this.formulario.controls.passwordTemporal.reset('');
          if (nueva) {
            this.referencia
              .afterClosed()
              .pipe(take(1))
              .subscribe(() =>
                this.dialogos.open(PasswordTemporalDialogo, {
                  data: datos.passwordTemporal,
                  width: '480px',
                  maxWidth: 'calc(100vw - 32px)',
                  autoFocus: 'first-tabbable',
                }),
              );
          }
          this.referencia.close(true);
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
}
