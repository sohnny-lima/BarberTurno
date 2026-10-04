import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin, Subscription } from 'rxjs';
import { BarberosApi } from '../../core/api/barberos-api';
import { ReportesApi } from '../../core/api/reportes-api';
import { ReservasApi } from '../../core/api/reservas-api';
import { ServiciosApi } from '../../core/api/servicios-api';
import { BarberoDto, ServicioDto } from '../../core/modelos/catalogo';
import { ConsultaResumen, ResumenReporteDto } from '../../core/modelos/reportes';
import { EstadoReserva, ReservaDto } from '../../core/modelos/reservas';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { instanteLima } from '../../core/tiempo/instante-lima';
import { mesLima } from '../../core/tiempo/mes-lima';
import { EstadoReservaChip, ESTADOS_RESERVA } from '../../shared/estado-reserva-chip';
import { mostrarErrores } from '../../shared/formulario';
import { paginadorEspanol } from '../../shared/paginador-es';
import { rangoReporte } from '../../shared/rango-reporte';

/** Conserva las seis categorías del servidor, incluso cuando todas valen cero. */
export function tarjetasResumen(resumen: ResumenReporteDto) {
  return (Object.keys(ESTADOS_RESERVA) as EstadoReserva[]).map((estado) => ({
    estado,
    total: resumen.porEstado[estado] ?? 0,
  }));
}

@Component({
  selector: 'app-reportes',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    FechaLimaPipe,
    EstadoReservaChip,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: paginadorEspanol }],
  templateUrl: './reportes.html',
  styleUrl: './reportes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Reportes {
  private readonly api = inject(ReportesApi);
  private readonly reservasApi = inject(ReservasApi);
  private readonly serviciosApi = inject(ServiciosApi);
  private readonly barberosApi = inject(BarberosApi);
  private readonly destroyRef = inject(DestroyRef);
  private peticion?: Subscription;
  private catalogos?: Subscription;
  private readonly mes = mesLima();
  readonly formulario = new FormGroup(
    {
      desde: new FormControl(this.mes.desde, {
        nonNullable: true,
        validators: Validators.required,
      }),
      hasta: new FormControl(this.mes.hasta, {
        nonNullable: true,
        validators: Validators.required,
      }),
      servicioId: new FormControl<number | null>(null),
      barberoId: new FormControl<number | null>(null),
    },
    { validators: rangoReporte },
  );
  readonly servicios = signal<ServicioDto[]>([]);
  readonly barberos = signal<BarberoDto[]>([]);
  readonly errorCatalogos = signal('');
  readonly resumen = signal<ResumenReporteDto | null>(null);
  readonly tarjetas = computed(() => (this.resumen() ? tarjetasResumen(this.resumen()!) : []));
  readonly estadosCoinciden = computed(
    () =>
      this.resumen() !== null &&
      this.tarjetas().reduce((total, tarjeta) => total + tarjeta.total, 0) ===
        this.resumen()!.total,
  );
  readonly filas = signal<ReservaDto[]>([]);
  readonly filtrosAplicados = signal<ConsultaResumen | null>(null);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly mensajeHistorial = signal('');
  readonly pagina = signal(0);
  readonly tamano = signal(10);
  readonly total = signal(0);
  readonly instanteLima = instanteLima;

  constructor() {
    this.cargarCatalogos();
    this.aplicar();
  }

  cargarCatalogos() {
    this.catalogos?.unsubscribe();
    this.errorCatalogos.set('');
    // Los inactivos también pueden tener reservas en el historial.
    this.catalogos = forkJoin({
      servicios: this.serviciosApi.listar(true),
      barberos: this.barberosApi.listar(true),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (datos) => {
          this.servicios.set(datos.servicios);
          this.barberos.set(datos.barberos);
        },
        error: (error) => this.errorCatalogos.set(mostrarErrores(new FormGroup({}), error)),
      });
  }

  aplicar() {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) return;
    this.peticion?.unsubscribe();
    const { desde, hasta, servicioId, barberoId } = this.formulario.getRawValue();
    const consulta: ConsultaResumen = {
      desde,
      hasta,
      ...(servicioId !== null ? { servicioId } : {}),
      ...(barberoId !== null ? { barberoId } : {}),
    };
    this.filtrosAplicados.set(consulta);
    this.resumen.set(null);
    this.filas.set([]);
    this.total.set(0);
    this.pagina.set(0);
    this.mensaje.set('');
    this.mensajeHistorial.set('');
    this.cargando.set(true);
    // Ambas suscripciones comienzan juntas y se presentan como un mismo resultado.
    this.peticion = forkJoin({
      resumen: this.api.resumen(consulta),
      historial: this.reservasApi.agenda({ ...consulta, pagina: 0, tamano: this.tamano() }),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: ({ resumen, historial }) => {
          this.resumen.set(resumen);
          this.filas.set(historial.contenido);
          this.total.set(historial.totalElementos);
        },
        error: (error) => this.mensaje.set(mostrarErrores(this.formulario, error)),
      });
  }

  paginar(evento: PageEvent) {
    if (this.cargando() || !this.resumen()) return;
    this.pagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.cargarPagina();
  }

  cargarPagina() {
    const consulta = this.filtrosAplicados();
    if (!consulta || !this.resumen()) return;
    this.peticion?.unsubscribe();
    this.mensajeHistorial.set('');
    this.filas.set([]);
    this.cargando.set(true);
    this.peticion = this.reservasApi
      .agenda({ ...consulta, pagina: this.pagina(), tamano: this.tamano() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (datos) => {
          this.filas.set(datos.contenido);
          this.total.set(datos.totalElementos);
          this.pagina.set(datos.pagina);
        },
        error: (error) => this.mensajeHistorial.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
}
