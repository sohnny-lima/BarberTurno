import { BreakpointObserver } from '@angular/cdk/layout';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, interval, map } from 'rxjs';
import { BandejaWhatsAppApi } from '../../core/api/bandeja-whatsapp-api';
import {
  ConversacionBandejaDto,
  DetalleConversacionDto,
  MensajeBandejaDto,
  MotivoDerivacion,
  MotivoNoResponder,
} from '../../core/modelos/bandeja-whatsapp';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { EstadoContenido } from '../../shared/estado-contenido';
import { mostrarErrores } from '../../shared/formulario';

/** Diálogo de confirmación para devolver una conversación al asistente virtual. */
@Component({
  selector: 'app-confirmar-devolucion-dialogo',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Devolver al asistente</h2>
    <mat-dialog-content>
      <p>¿Desea devolver la conversación de {{ datos.cliente }} al asistente virtual?</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancelar</button>
      <button mat-flat-button [mat-dialog-close]="true">Devolver</button>
    </mat-dialog-actions>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmarDevolucionDialogo {
  readonly datos = inject<{ cliente: string }>(MAT_DIALOG_DATA);
}

/**
 * Pantalla de la bandeja web de WhatsApp para el personal de gestión (PROPIETARIO y ENCARGADO).
 * Permite listar conversaciones derivadas, consultar el historial, responder dentro de la ventana
 * y devolver la atención al asistente virtual.
 */
@Component({
  selector: 'app-bandeja-whatsapp',
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    CabeceraPagina,
    EstadoContenido,
    FechaLimaPipe,
  ],
  providers: [BandejaWhatsAppApi],
  templateUrl: './bandeja.html',
  styleUrl: './bandeja.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Bandeja {
  readonly negocio = inject(NegocioActual);
  private readonly api = inject(BandejaWhatsAppApi);
  private readonly dialogos = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly movil = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 767.98px)')
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  readonly conversaciones = signal<ConversacionBandejaDto[]>([]);
  readonly cargandoLista = signal(false);
  readonly errorLista = signal('');

  readonly seleccionadaId = signal<number | null>(null);
  readonly detalle = signal<DetalleConversacionDto | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal('');
  readonly cargandoAnteriores = signal(false);

  readonly textoRespuesta = signal('');
  readonly enviando = signal(false);
  readonly devolviendo = signal(false);
  readonly mensajeEnvio = signal('');
  readonly mensajeAccion = signal('');

  readonly nombresMotivo: Record<MotivoDerivacion, string> = {
    PETICION: 'Petición del cliente',
    SIN_PROGRESO: 'Sin progreso',
    LIMITE: 'Límite alcanzado',
    HERRAMIENTA: 'Herramienta',
  };

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const idStr = params.get('id');
      const id = idStr ? Number(idStr) : null;
      this.seleccionadaId.set(id);
      if (id) {
        this.cargarDetalle(id, true);
      } else {
        this.detalle.set(null);
        this.errorDetalle.set('');
      }
    });

    // Sondeo periódico cada 30 segundos sólo mientras la pantalla está activa
    interval(30_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.cargarLista(false);
        const id = this.seleccionadaId();
        if (id) {
          this.cargarDetalle(id, false);
        }
      });

    this.cargarLista(true);
  }

  cargarLista(mostrarCarga = true) {
    if (mostrarCarga) this.cargandoLista.set(true);
    this.errorLista.set('');
    this.api
      .listar(0, 50)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (mostrarCarga) this.cargandoLista.set(false);
        }),
      )
      .subscribe({
        next: (pagina) => {
          this.conversaciones.set(pagina.contenido);
        },
        error: (error: HttpErrorResponse) => {
          this.errorLista.set(mostrarErrores(new FormGroup({}), error));
        },
      });
  }

  cargarDetalle(id: number, mostrarCarga = true) {
    if (mostrarCarga) this.cargandoDetalle.set(true);
    this.errorDetalle.set('');
    this.api
      .detalle(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (mostrarCarga) this.cargandoDetalle.set(false);
        }),
      )
      .subscribe({
        next: (datos) => {
          this.detalle.set(datos);
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 404) {
            this.errorDetalle.set('Esta conversación ya no está derivada');
          } else {
            this.errorDetalle.set(mostrarErrores(new FormGroup({}), error));
          }
        },
      });
  }

  verAnteriores() {
    const det = this.detalle();
    if (!det || !det.hayAnteriores || det.mensajes.length === 0 || this.cargandoAnteriores()) {
      return;
    }
    const primerMensajeId = det.mensajes[0].id;
    this.cargandoAnteriores.set(true);
    this.api
      .detalle(det.conversacion.id, primerMensajeId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoAnteriores.set(false)),
      )
      .subscribe({
        next: (anteriores) => {
          this.detalle.set({
            conversacion: anteriores.conversacion,
            mensajes: [...anteriores.mensajes, ...det.mensajes],
            hayAnteriores: anteriores.hayAnteriores,
          });
        },
        error: (error: HttpErrorResponse) => {
          this.errorDetalle.set(mostrarErrores(new FormGroup({}), error));
        },
      });
  }

  enviarRespuesta() {
    const det = this.detalle();
    const texto = this.textoRespuesta().trim();
    if (!det || !texto || this.enviando() || !det.conversacion.puedeResponder) {
      return;
    }
    this.enviando.set(true);
    this.mensajeEnvio.set('');
    this.errorDetalle.set('');
    this.api
      .responder(det.conversacion.id, texto)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.enviando.set(false)),
      )
      .subscribe({
        next: (mensajeCreado) => {
          this.textoRespuesta.set('');
          this.mensajeEnvio.set('Mensaje enviado correctamente');
          this.detalle.update((actual) =>
            actual
              ? {
                  ...actual,
                  mensajes: [...actual.mensajes, mensajeCreado],
                }
              : null,
          );
          this.cargarLista(false);
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 422) {
            this.errorDetalle.set(
              'La ventana de 24 h de WhatsApp está cerrada: solo puede responder cuando el cliente vuelva a escribir',
            );
          } else if (error.status === 404) {
            this.errorDetalle.set('Esta conversación ya no está derivada');
          } else {
            this.errorDetalle.set(mostrarErrores(new FormGroup({}), error));
          }
        },
      });
  }

  devolver() {
    const det = this.detalle();
    if (!det || this.devolviendo()) return;
    const dialogo = this.dialogos.open(ConfirmarDevolucionDialogo, {
      data: { cliente: det.conversacion.cliente },
    });
    dialogo
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmado) => {
        if (!confirmado) return;
        this.devolviendo.set(true);
        this.errorDetalle.set('');
        this.api
          .devolver(det.conversacion.id)
          .pipe(
            takeUntilDestroyed(this.destroyRef),
            finalize(() => this.devolviendo.set(false)),
          )
          .subscribe({
            next: () => {
              this.mensajeAccion.set('Conversación devuelta al asistente');
              void this.router.navigate([this.negocio.ruta('whatsapp')]);
              this.cargarLista(true);
            },
            error: (error: HttpErrorResponse) => {
              if (error.status === 404) {
                this.errorDetalle.set('Esta conversación ya no está derivada');
              } else {
                this.errorDetalle.set(mostrarErrores(new FormGroup({}), error));
              }
            },
          });
      });
  }

  actualizar() {
    this.cargarLista(true);
    const id = this.seleccionadaId();
    if (id) {
      this.cargarDetalle(id, true);
    }
  }

  motivoLegible(motivo: MotivoDerivacion): string {
    return this.nombresMotivo[motivo] ?? motivo;
  }

  origenLegible(mensaje: MensajeBandejaDto): string {
    switch (mensaje.origen) {
      case 'CLIENTE':
        return 'Cliente';
      case 'ASISTENTE':
        return 'Asistente';
      case 'SISTEMA':
        return 'Sistema';
      case 'PERSONA':
        return mensaje.enviadoPor ? mensaje.enviadoPor : 'Personal';
      default:
        return mensaje.origen;
    }
  }

  explicacionNoResponder(motivo: MotivoNoResponder | null): string {
    if (motivo === 'FUERA_DE_VENTANA') {
      return 'La ventana de 24 h de WhatsApp está cerrada: solo puede responder cuando el cliente vuelva a escribir';
    }
    if (motivo === 'NEGOCIO_NO_OPERATIVO') {
      return 'El canal de WhatsApp de la barbería no está disponible';
    }
    return 'No se puede responder en este momento';
  }
}
