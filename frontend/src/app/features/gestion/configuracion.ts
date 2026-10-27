import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { finalize } from 'rxjs';
import { ConfiguracionApi } from '../../core/api/configuracion-api';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { EstadoContenido } from '../../shared/estado-contenido';
import { errorCampo, mostrarErrores } from '../../shared/formulario';
import { SeccionFormulario } from '../../shared/seccion-formulario';

const numero = (min: number) =>
  new FormControl(min, {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.min(min),
      Validators.max(32767),
      Validators.pattern('^[0-9]+$'),
    ],
  });
@Component({
  selector: 'app-configuracion',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    CabeceraPagina,
    EstadoContenido,
    SeccionFormulario,
  ],
  providers: [ConfiguracionApi],
  templateUrl: './configuracion.html',
  styleUrl: './gestion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Configuracion {
  private readonly api = inject(ConfiguracionApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly negocio = inject(NegocioActual);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  readonly errorCarga = signal('');
  readonly guardado = signal(false);
  readonly errorCampo = errorCampo;
  readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    horizonteDias: numero(1),
    anticipacionCambioClienteMin: numero(0),
    limiteReservasActivas: numero(1),
    limiteBarberosActivos: numero(1),
    toleranciaInicioMin: numero(0),
    confirmacionManual: new FormControl(false, { nonNullable: true }),
  });
  readonly campos = [
    {
      clave: 'horizonteDias',
      etiqueta: 'Horizonte en días',
      ayuda: 'Anticipación máxima para reservar.',
      min: 1,
    },
    {
      clave: 'anticipacionCambioClienteMin',
      etiqueta: 'Anticipación para cambios del cliente en minutos',
      ayuda: 'Tiempo previo necesario para que el cliente cambie su cita.',
      min: 0,
    },
    {
      clave: 'limiteReservasActivas',
      etiqueta: 'Máximo de reservas activas por cliente',
      ayuda: 'Cupo de reservas futuras por ficha.',
      min: 1,
    },
    {
      clave: 'limiteBarberosActivos',
      etiqueta: 'Límite de barberos activos',
      ayuda: 'Cupo de perfiles profesionales activos.',
      min: 1,
    },
    {
      clave: 'toleranciaInicioMin',
      etiqueta: 'Tolerancia de inicio en minutos',
      ayuda: 'Adelanto permitido para iniciar la atención.',
      min: 0,
    },
  ] as const;
  constructor() {
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.mensaje.set('');
    this.errorCarga.set('');
    this.api
      .consultar()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (datos) => this.formulario.reset(datos),
        error: (error) => this.errorCarga.set(mostrarErrores(this.formulario, error)),
      });
  }
  guardar() {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.guardando()) return;
    this.guardando.set(true);
    this.guardado.set(false);
    this.mensaje.set('');
    this.errorCarga.set('');
    this.api
      .guardar(this.formulario.getRawValue())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: (datos) => {
          this.formulario.reset(datos);
          this.guardado.set(true);
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
}
