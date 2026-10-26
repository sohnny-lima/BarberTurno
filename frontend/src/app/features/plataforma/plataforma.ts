import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { finalize, map } from 'rxjs';
import { PlataformaApi } from '../../core/api/plataforma-api';
import { NegocioPlataformaDto } from '../../core/modelos/plataforma';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { ConfirmarEstadoDialogo } from '../../shared/confirmar-estado-dialogo';
import { EnlaceInvitacion } from '../../shared/enlace-invitacion';
import { EstadoContenido } from '../../shared/estado-contenido';
import { mostrarErrores } from '../../shared/formulario';
import { ListaAdaptable } from '../../shared/lista-adaptable';
import { AltaNegocio } from './alta-negocio';

@Component({
  selector: 'app-plataforma',
  imports: [
    NgTemplateOutlet,
    MatButtonModule,
    RouterLink,
    CabeceraPagina,
    EnlaceInvitacion,
    EstadoContenido,
    ListaAdaptable,
  ],
  templateUrl: './plataforma.html',
  styleUrls: ['../admin/catalogo-lista.scss', '../gestion/gestion.scss', './plataforma.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Plataforma {
  private readonly api = inject(PlataformaApi);
  private readonly dialogos = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly filas = signal<NegocioPlataformaDto[]>([]);
  readonly enlace = signal<{ valor: string; expiraEn: string | null } | null>(null);
  readonly cargando = signal(false);
  readonly operando = signal(false);
  readonly mensaje = signal('');
  constructor() {
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.mensaje.set('');
    this.api
      .listar()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (filas) => this.filas.set(filas),
        error: (error) => this.mensaje.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
  crear() {
    if (this.operando()) return;
    this.operando.set(true);
    this.enlace.set(null);
    this.dialogos
      .open(AltaNegocio, {
        width: '520px',
        maxWidth: 'calc(100vw - 32px)',
        maxHeight: 'calc(100dvh - 32px)',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.operando.set(false)),
      )
      .subscribe((creado) => {
        if (creado) {
          this.enlace.set({ valor: creado.enlaceInvitacion, expiraEn: null });
          this.cargar();
        }
      });
  }
  cambiarEstado(fila: NegocioPlataformaDto) {
    const suspender = fila.estado === 'ACTIVO';
    this.confirmar(
      fila,
      suspender ? 'Suspender barbería' : 'Reactivar barbería',
      suspender
        ? 'El público no podrá acceder. El personal podrá consultar, pero no hacer cambios.'
        : 'La barbería volverá a estar disponible.',
      suspender,
      () => this.api.cambiarEstado(fila.id, suspender ? 'SUSPENDIDO' : 'ACTIVO'),
    );
  }
  reenviar(fila: NegocioPlataformaDto) {
    this.confirmar(
      fila,
      'Reenviar invitación',
      'Se invalidará el enlace anterior y se mostrará uno nuevo una sola vez.',
      true,
      () =>
        this.api.reenviar(fila.id).pipe(
          map((emitida) => {
            this.enlace.set({
              valor: emitida.enlaceInvitacion,
              expiraEn: emitida.invitacion.expiraEn,
            });
          }),
        ),
    );
  }
  revocar(fila: NegocioPlataformaDto) {
    this.confirmar(
      fila,
      'Revocar invitación',
      'El enlace de la invitación inicial dejará de ser válido.',
      true,
      () => this.api.revocar(fila.id),
    );
  }
  private confirmar(
    fila: NegocioPlataformaDto,
    titulo: string,
    texto: string,
    riesgo: boolean,
    cambiar: () => import('rxjs').Observable<void>,
  ) {
    if (this.operando()) return;
    this.operando.set(true);
    this.enlace.set(null);
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        maxHeight: 'calc(100dvh - 32px)',
        data: {
          nombre: fila.nombre,
          titulo,
          texto: fila.nombre + '. ' + texto,
          boton: titulo,
          activar: true,
          riesgo,
          cambiar: () => cambiar().pipe(map(() => 0)),
        },
      })
      .afterClosed()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.operando.set(false)),
      )
      .subscribe((hecho) => {
        if (hecho) this.cargar();
      });
  }
}
