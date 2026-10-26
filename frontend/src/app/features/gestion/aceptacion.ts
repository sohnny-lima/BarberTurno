import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { InvitacionApi } from '../../core/api/invitacion-api';
import { MisNegociosApi } from '../../core/api/mis-negocios-api';
import { SesionService } from '../../core/auth/sesion-service';
import { InvitacionPublicaDto } from '../../core/modelos/gestion';
import { NOMBRES_PAPEL } from '../../core/modelos/negocios';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { EstadoContenido } from '../../shared/estado-contenido';
import { confirmarPassword, errorCampo, mostrarErrores } from '../../shared/formulario';
import { SeccionFormulario } from '../../shared/seccion-formulario';

@Component({
  selector: 'app-aceptacion',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    RouterLink,
    CabeceraPagina,
    EstadoContenido,
    SeccionFormulario,
  ],
  templateUrl: './aceptacion.html',
  styleUrl: './aceptacion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Aceptacion {
  private readonly api = inject(InvitacionApi);
  private readonly negocios = inject(MisNegociosApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly sesion = inject(SesionService);
  // Solo memoria de la vista: nunca se persiste ni registra la capacidad.
  private readonly token = inject(ActivatedRoute).snapshot.paramMap.get('token')!;
  readonly retorno = '/invitacion/' + this.token;
  readonly invitacion = signal<InvitacionPublicaDto | null>(null);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly registro = signal(false);
  readonly mensaje = signal('');
  readonly aviso = signal('');
  readonly nombres = NOMBRES_PAPEL;
  readonly errorCampo = errorCampo;
  readonly estados = {
    CADUCADA: 'Esta invitación caducó. Pida una nueva a la barbería.',
    REVOCADA: 'Esta invitación ya no es válida.',
    USADA: 'Esta invitación ya se usó.',
    PENDIENTE: '',
  };
  readonly formulario = new FormGroup(
    {
      nombre: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(100)],
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
  constructor() {
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.api
      .consultar(this.token)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (dato) => {
          this.invitacion.set(dato);
          this.aviso.set(this.estados[dato.estado]);
        },
        error: (error: HttpErrorResponse) =>
          this.aviso.set(
            error.status === 404
              ? 'Esta invitación ya no es válida.'
              : mostrarErrores(this.formulario, error),
          ),
      });
  }
  aceptar() {
    if (this.guardando()) return;
    this.operar(this.api.aceptar(this.token));
  }
  crearCuenta() {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.guardando()) return;
    const { nombre, telefono, password, aceptaPrivacidad } = this.formulario.getRawValue();
    this.operar(
      this.api
        .registrar(this.token, { nombre, telefono, password, aceptaPrivacidad })
        .pipe(switchMap(() => this.sesion.cargar())),
    );
  }
  private operar(operacion: import('rxjs').Observable<unknown>) {
    this.guardando.set(true);
    this.mensaje.set('');
    operacion
      .pipe(
        switchMap(() => this.negocios.listar()),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: (filas) => {
          this.formulario.reset();
          const destinos = filas.filter(
            (f) => f.nombre === this.invitacion()?.negocio && f.rol === this.invitacion()?.rol,
          );
          // La consulta pública no revela el slug. Si el nombre no identifica una sola
          // relación autorizada, el selector permite elegirla sin adivinar un destino.
          void this.router.navigateByUrl(
            destinos.length === 1 ? '/n/' + destinos[0].slug + '/agenda' : '/negocios',
          );
        },
        error: (error: HttpErrorResponse) => {
          this.formulario.controls.password.reset();
          this.formulario.controls.confirmacion.reset();
          if (error.error?.codigo === 'INVITACION_OTRA_CUENTA')
            this.aviso.set('Esta invitación es para otra cuenta. Ingrese con el correo invitado.');
          else if (error.error?.codigo === 'INVITACION_NO_VIGENTE') this.cargar();
          else this.mensaje.set(mostrarErrores(this.formulario, error));
        },
      });
  }
}
