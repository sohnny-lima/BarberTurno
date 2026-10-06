import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { fechaPresentacion } from '../../shared/fecha-presentacion';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY, expand, finalize, Observable, reduce, Subscription } from 'rxjs';
import { BarberosApi } from '../../core/api/barberos-api';
import { ReservasApi } from '../../core/api/reservas-api';
import { SesionService } from '../../core/auth/sesion-service';
import { BarberoDto } from '../../core/modelos/catalogo';
import { EstadoTransicion, ReservaDto } from '../../core/modelos/reservas';
import { AvisosService } from '../../core/notificaciones/avisos-service';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { fechaHoyLima, instanteLima } from '../../core/tiempo/instante-lima';
import { semanaLima, sumarDias } from '../../core/tiempo/semana-lima';
import { ESTADOS_RESERVA, EstadoReservaChip } from '../../shared/estado-reserva-chip';
import { mostrarErrores } from '../../shared/formulario';
import { CancelarDialogo } from '../mis-citas/cancelar-dialogo';
import { AuditoriaDialogo } from './auditoria-dialogo';
import { ReprogramarDialogo } from './reprogramar-dialogo';
import { ACCIONES_TRANSICION, ResultadoAgenda, TransicionDialogo } from './transicion-dialogo';

const fechaValida: ValidatorFn = (control) => {
  const valor = String(control.value ?? '');
  const fecha = new Date(valor + 'T12:00:00Z');
  return /^\d{4}-\d{2}-\d{2}$/.test(valor) &&
    !Number.isNaN(fecha.getTime()) &&
    fecha.toISOString().slice(0, 10) === valor
    ? null
    : { fecha: true };
};
@Component({
  selector: 'app-agenda',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    FechaLimaPipe,
    EstadoReservaChip,
  ],
  templateUrl: './agenda.html',
  styleUrl: './agenda.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Agenda {
  private readonly api = inject(ReservasApi);
  private readonly barberosApi = inject(BarberosApi);
  private readonly dialogos = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);
  private readonly avisos = inject(AvisosService);
  private readonly destroyRef = inject(DestroyRef);
  readonly sesion = inject(SesionService);
  private peticion?: Subscription;
  readonly admin = computed(() => this.sesion.rol() === 'ADMIN');
  readonly formulario = new FormGroup({
    fecha: new FormControl(fechaHoyLima(), {
      nonNullable: true,
      validators: [Validators.required, fechaValida],
    }),
    vista: new FormControl<'dia' | 'semana'>('dia', { nonNullable: true }),
    barberoId: new FormControl<number | null>(null),
  });
  readonly barberos = signal<BarberoDto[]>([]);
  readonly filas = signal<ReservaDto[]>([]);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly errorBarberos = signal('');
  readonly periodo = signal({ desde: fechaHoyLima(), hasta: fechaHoyLima() });
  readonly todos = signal(true);
  readonly acciones = Object.entries(ACCIONES_TRANSICION) as [EstadoTransicion, string][];
  readonly grupos = computed(() => {
    const { desde, hasta } = this.periodo();
    const grupos: { fecha: string; instante: string; filas: ReservaDto[] }[] = [];
    for (let fecha = desde; fecha <= hasta; fecha = sumarDias(fecha, 1)) {
      grupos.push({
        fecha,
        instante: instanteLima(fecha, '00:00'),
        filas: this.filas().filter((r) => fechaHoyLima(0, new Date(r.inicio)) === fecha),
      });
    }
    return grupos;
  });
  readonly fecha = fechaPresentacion;
  readonly hoy = fechaHoyLima();
  readonly conteos = computed(() =>
    Object.entries(ESTADOS_RESERVA)
      .map(([estado, nombre]) => ({
        estado,
        nombre,
        total: this.filas().filter((r) => r.estado === estado).length,
      }))
      .filter((grupo) => grupo.total > 0),
  );
  readonly posicionAhora = computed(() => {
    const periodo = this.periodo();
    if (periodo.desde !== this.hoy || periodo.hasta !== this.hoy) return -1;
    const posicion = this.filas().findIndex((r) => Date.parse(r.inicio) > Date.now());
    return posicion === -1 ? this.filas().length : posicion;
  });
  primeraTransicion(reserva: ReservaDto) {
    return this.acciones.find(([estado]) => reserva.permisos.transiciones.includes(estado))?.[0];
  }
  moverFecha(direccion: number) {
    const { fecha, vista } = this.formulario.getRawValue();
    this.formulario.controls.fecha.setValue(
      sumarDias(fecha, direccion * (vista === 'semana' ? 7 : 1)),
    );
    this.cargar();
  }
  irHoy() {
    this.formulario.controls.fecha.setValue(this.hoy);
    this.cargar();
  }
  constructor() {
    if (this.admin()) this.cargarBarberos();
    this.cargar();
  }
  cargarBarberos() {
    this.errorBarberos.set('');
    this.barberosApi
      .listar(true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (datos) => this.barberos.set(datos),
        error: (error) => this.errorBarberos.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
  cargar() {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    this.peticion?.unsubscribe();
    const { fecha, vista, barberoId } = this.formulario.getRawValue();
    const periodo = vista === 'semana' ? semanaLima(fecha) : { desde: fecha, hasta: fecha };
    this.periodo.set(periodo);
    this.todos.set(this.admin() && barberoId === null);
    this.filas.set([]);
    this.mensaje.set('');
    this.cargando.set(true);
    const consulta = {
      ...periodo,
      tamano: 100,
      ...(this.admin() && barberoId !== null ? { barberoId } : {}),
    };
    this.peticion = this.api
      .agenda({ ...consulta, pagina: 0 })
      .pipe(
        // Lee todas las páginas del periodo; no omite citas cuando hay más de cien.
        expand((pagina) =>
          pagina.pagina + 1 < pagina.totalPaginas
            ? this.api.agenda({ ...consulta, pagina: pagina.pagina + 1 })
            : EMPTY,
        ),
        reduce((filas, pagina) => [...filas, ...pagina.contenido], [] as ReservaDto[]),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (filas) =>
          this.filas.set(
            filas.sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio) || a.id - b.id),
          ),
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }
  transicionar(reserva: ReservaDto, estado: EstadoTransicion) {
    if (!reserva.permisos.transiciones.includes(estado)) return;
    this.trasCambio(
      this.dialogos
        .open(TransicionDialogo, {
          data: { reserva, estado },
          width: '480px',
          maxWidth: 'calc(100vw - 32px)',
        })
        .afterClosed(),
    );
  }
  cancelar(reserva: ReservaDto) {
    if (!this.admin() || !reserva.permisos.cancelar) return;
    this.trasCambio(
      this.dialogos
        .open(CancelarDialogo, {
          data: { ...reserva, motivoObligatorio: true },
          width: '480px',
          maxWidth: 'calc(100vw - 32px)',
        })
        .afterClosed(),
    );
  }
  reprogramar(reserva: ReservaDto) {
    if (!this.admin() || !reserva.permisos.reprogramar) return;
    this.trasCambio(
      this.dialogos
        .open(ReprogramarDialogo, {
          data: { reserva, barberos: this.barberos() },
          width: '540px',
          maxWidth: 'calc(100vw - 32px)',
        })
        .afterClosed(),
    );
  }
  verCambios(reserva: ReservaDto) {
    this.dialogos.open(AuditoriaDialogo, {
      data: reserva,
      width: '760px',
      maxWidth: 'calc(100vw - 32px)',
    });
  }
  private trasCambio(resultado: Observable<ResultadoAgenda | 'cancelada' | undefined>) {
    resultado.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((valor) => {
      if (!valor) return;
      this.snackbar.open(
        valor === 'actualizada'
          ? 'La agenda cambió. Recargamos los datos; revise la cita antes de intentar nuevamente.'
          : 'La cita fue actualizada.',
        'Cerrar',
        { duration: 6000 },
      );
      this.cargar();
      this.avisos.actualizar();
    });
  }
}
