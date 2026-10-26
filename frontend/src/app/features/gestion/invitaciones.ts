import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, map } from 'rxjs';
import { InvitacionesApi } from '../../core/api/invitaciones-api';
import { InvitacionDto, InvitacionEmitidaDto } from '../../core/modelos/gestion';
import { NOMBRES_PAPEL, RolPersonal } from '../../core/modelos/negocios';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { ConfirmarEstadoDialogo } from '../../shared/confirmar-estado-dialogo';
import { EnlaceInvitacion } from '../../shared/enlace-invitacion';
import { EstadoContenido } from '../../shared/estado-contenido';
import { errorCampo, mostrarErrores } from '../../shared/formulario';
import { ListaAdaptable } from '../../shared/lista-adaptable';
import { SeccionFormulario } from '../../shared/seccion-formulario';

@Component({
  selector: 'app-invitaciones',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    FechaLimaPipe,
    CabeceraPagina,
    EnlaceInvitacion,
    EstadoContenido,
    ListaAdaptable,
    SeccionFormulario,
  ],
  providers: [InvitacionesApi],
  templateUrl: './invitaciones.html',
  styleUrls: ['../admin/catalogo-lista.scss', './gestion.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Invitaciones {
  private readonly api = inject(InvitacionesApi);
  private readonly negocio = inject(NegocioActual);
  private readonly dialogos = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly filas = signal<InvitacionDto[]>([]);
  readonly emitida = signal<InvitacionEmitidaDto | null>(null);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  readonly nombres = NOMBRES_PAPEL;
  readonly errorCampo = errorCampo;
  readonly roles = (): RolPersonal[] =>
    this.negocio.usuario()?.rolNegocio === 'PROPIETARIO'
      ? ['PROPIETARIO', 'ENCARGADO', 'BARBERO']
      : this.negocio.usuario()?.rolNegocio === 'ENCARGADO'
        ? ['BARBERO']
        : [];
  readonly puedeRevocar = (fila: InvitacionDto) => this.roles().includes(fila.rol);
  readonly formulario = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    rol: new FormControl<RolPersonal>('BARBERO', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });
  constructor() {
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.api
      .listar()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (filas) => this.filas.set(filas),
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
  crear() {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.guardando()) return;
    this.emitida.set(null);
    this.mensaje.set('');
    this.guardando.set(true);
    this.api
      .crear(this.formulario.getRawValue())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: (emitida) => {
          this.emitida.set(emitida);
          this.formulario.controls.correo.reset();
          this.cargar();
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
  revocar(fila: InvitacionDto) {
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        data: {
          nombre: fila.correo,
          titulo: 'Revocar invitación',
          texto: 'El enlace dejará de ser válido.',
          boton: 'Revocar invitación',
          activar: true,
          riesgo: true,
          cambiar: () => this.api.revocar(fila.id).pipe(map(() => 0)),
        },
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((hecho) => {
        if (hecho) {
          this.emitida.set(null);
          this.cargar();
        }
      });
  }
}
