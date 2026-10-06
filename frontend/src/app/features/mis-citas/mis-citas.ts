import { RouterLink } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { finalize, Subscription } from 'rxjs';
import { ReservasApi } from '../../core/api/reservas-api';
import { EstadoReserva, ReservaDto } from '../../core/modelos/reservas';
import { AvisosService } from '../../core/notificaciones/avisos-service';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';
import { ESTADOS_RESERVA } from '../../shared/estado-reserva-chip';
import { mostrarErrores } from '../../shared/formulario';
import { paginadorEspanol } from '../../shared/paginador-es';
import { ReservaTarjeta } from '../../shared/reserva-tarjeta';
import { AvisosPanel } from './avisos-panel';
import { CancelarDialogo, ResultadoCancelacion } from './cancelar-dialogo';

export const rangoFechas: ValidatorFn = (grupo) => {
  const desde = grupo.get('desde')?.value as string;
  const hasta = grupo.get('hasta')?.value as string;
  return desde && hasta && desde > hasta ? { rangoFechas: true } : null;
};
@Component({
  selector: 'app-mis-citas',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTabsModule,
    ReservaTarjeta,
    AvisosPanel,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: paginadorEspanol }],
  templateUrl: './mis-citas.html',
  styleUrl: './mis-citas.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MisCitas {
  private readonly api = inject(ReservasApi);
  private readonly dialogos = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);
  private readonly avisos = inject(AvisosService);
  private readonly destroyRef = inject(DestroyRef);
  private peticion?: Subscription;
  readonly formulario = new FormGroup(
    {
      desde: new FormControl('', { nonNullable: true }),
      hasta: new FormControl('', { nonNullable: true }),
      estado: new FormControl<EstadoReserva | ''>('', { nonNullable: true }),
    },
    { validators: rangoFechas },
  );
  readonly estados = Object.entries(ESTADOS_RESERVA);
  readonly filtrosAbiertos = signal(false);
  readonly historial = signal(false);
  readonly filas = signal<ReservaDto[]>([]);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly pagina = signal(0);
  readonly tamano = signal(10);
  readonly total = signal(0);
  readonly revisionAvisos = signal(0);
  constructor() {
    this.cargar();
  }
  cambiarTab(indice: number) {
    this.historial.set(indice === 1);
    this.filtrar();
  }
  filtrar() {
    this.pagina.set(0);
    this.cargar();
  }
  paginar(evento: PageEvent) {
    this.pagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.cargar();
  }
  cargar() {
    this.formulario.markAllAsTouched();
    this.peticion?.unsubscribe();
    if (this.formulario.invalid) return;
    const filtro = this.formulario.getRawValue();
    const hoy = fechaHoyLima();
    const ayer = fechaHoyLima(-1);
    const desde = this.historial()
      ? filtro.desde
      : !filtro.desde || filtro.desde < hoy
        ? hoy
        : filtro.desde;
    const hasta = this.historial()
      ? !filtro.hasta || filtro.hasta > ayer
        ? ayer
        : filtro.hasta
      : filtro.hasta;
    this.mensaje.set('');
    this.filas.set([]);
    this.total.set(0);
    if (desde && hasta && desde > hasta) return;
    this.cargando.set(true);
    this.peticion = this.api
      .mias({
        desde: desde || undefined,
        hasta: hasta || undefined,
        estado: filtro.estado || undefined,
        pagina: this.pagina(),
        tamano: this.tamano(),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (datos) => {
          this.filas.set(datos.contenido);
          this.total.set(datos.totalElementos);
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
  cancelar(reserva: ReservaDto) {
    this.dialogos
      .open<CancelarDialogo, ReservaDto, ResultadoCancelacion>(CancelarDialogo, {
        data: reserva,
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resultado) => {
        if (!resultado) return;
        this.snackbar.open(
          resultado === 'actualizada'
            ? 'La cita cambió. Recargamos los datos; revise su estado antes de intentar nuevamente.'
            : 'Su cita fue cancelada.',
          'Cerrar',
          { duration: 6000 },
        );
        this.cargar();
        this.avisos.actualizar();
        this.revisionAvisos.update((valor) => valor + 1);
      });
  }
}
