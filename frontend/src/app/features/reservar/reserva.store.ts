import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, EMPTY, finalize, forkJoin, of, Subject, switchMap } from 'rxjs';
import { BarberosApi } from '../../core/api/barberos-api';
import { DisponibilidadApi } from '../../core/api/disponibilidad-api';
import { ReservasApi } from '../../core/api/reservas-api';
import { ServiciosApi } from '../../core/api/servicios-api';
import { SesionService } from '../../core/auth/sesion-service';
import { BarberoDto, ServicioDto } from '../../core/modelos/catalogo';
import { ProblemDetail } from '../../core/modelos/identidad';
import { FranjaDto, ReservaDto } from '../../core/modelos/reservas';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';

const CLAVE = 'barberturno.reserva';
interface SeleccionGuardada {
  servicioId: number;
  preferencia: number | null;
  fecha: string;
  inicio: string;
  barberoId: number;
}

/** Estado de presentación: las franjas, permisos y políticas proceden del servidor. */
@Injectable()
export class ReservaStore {
  private readonly serviciosApi = inject(ServiciosApi);
  private readonly barberosApi = inject(BarberosApi);
  private readonly disponibilidadApi = inject(DisponibilidadApi);
  private readonly reservasApi = inject(ReservasApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly sesion = inject(SesionService);
  private readonly inicializaciones = new Subject<number | undefined>();
  private readonly consultas = new Subject<SeleccionGuardada | undefined>();
  readonly servicios = signal<ServicioDto[]>([]);
  readonly barberos = signal<BarberoDto[]>([]);
  readonly servicioId = signal<number | null>(null);
  readonly preferencia = signal<number | null>(null);
  readonly fecha = signal(fechaHoyLima());
  readonly franjas = signal<FranjaDto[]>([]);
  readonly duracionDisponibilidad = signal<number | null>(null);
  readonly franja = signal<FranjaDto | null>(null);
  readonly barberoId = signal<number | null>(null);
  readonly reserva = signal<ReservaDto | null>(null);
  readonly paso = signal(0);
  readonly cargando = signal(false);
  readonly consultando = signal(false);
  readonly enviando = signal(false);
  readonly mensaje = signal('');
  readonly limiteReservas = signal(false);
  readonly motivo = signal('');
  readonly resultado = signal<ReservaDto | null>(null);
  readonly servicio = computed(() => {
    const reserva = this.reserva();
    return reserva
      ? {
          ...reserva.servicio,
          duracionMin: this.duracionDisponibilidad() ?? reserva.duracionMin,
          precio: reserva.precioRef,
        }
      : (this.servicios().find((s) => s.id === this.servicioId()) ?? null);
  });
  readonly profesional = computed(() => this.barberos().find((b) => b.id === this.barberoId()));
  readonly pasoUnoCompleto = computed(() => !!this.servicio() && !this.cargando());
  readonly pasoDosCompleto = computed(
    () => !!this.franja() && !!this.barberoId() && !this.consultando(),
  );
  readonly puedeConfirmar = computed(
    () =>
      this.pasoDosCompleto() &&
      !this.enviando() &&
      this.sesion.rol() !== 'BARBERO' &&
      (!this.reserva() || this.reserva()!.permisos.reprogramar) &&
      (!this.reserva() || this.sesion.rol() !== 'ADMIN' || !!this.motivo().trim()),
  );

  constructor() {
    this.inicializaciones
      .pipe(
        switchMap((id) => {
          this.cargando.set(true);
          this.mensaje.set('');
          this.reserva.set(null);
          this.servicioId.set(null);
          this.preferencia.set(null);
          this.paso.set(0);
          this.fecha.set(fechaHoyLima());
          this.limpiarFranja();
          // Cancela también una consulta pendiente de la selección anterior.
          this.consultas.next(undefined);
          return forkJoin({
            servicios: this.serviciosApi.listar(),
            barberos: this.barberosApi.listar(),
            reserva: id === undefined ? of(null) : this.reservasApi.obtener(id),
          }).pipe(
            catchError((error: HttpErrorResponse) => {
              this.mostrarError(error);
              return EMPTY;
            }),
            finalize(() => this.cargando.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ servicios, barberos, reserva }) => {
        this.servicios.set(servicios.filter((s) => s.activo));
        this.barberos.set(barberos.filter((b) => b.activo));
        this.reserva.set(reserva);
        if (reserva) {
          this.servicioId.set(reserva.servicio.id);
          this.preferencia.set(reserva.barbero.id);
          this.fecha.set(fechaHoyLima(0, new Date(reserva.inicio)));
          if (!reserva.permisos.reprogramar) {
            this.mensaje.set(
              'Esta reserva no permite reprogramación. Consulte sus citas o contacte al administrador.',
            );
          }
        } else {
          this.restaurar();
        }
      });
    this.consultas
      .pipe(
        switchMap((guardada) => {
          this.limpiarFranja();
          const servicioId = this.servicioId();
          if (servicioId === null || this.cargando()) return EMPTY;
          this.consultando.set(true);
          return this.disponibilidadApi
            .consultar({
              servicioId,
              fecha: this.fecha(),
              ...(this.preferencia() === null ? {} : { barberoId: this.preferencia()! }),
              ...(this.reserva() ? { excluirReservaId: this.reserva()!.id } : {}),
            })
            .pipe(
              catchError((error: HttpErrorResponse) => {
                this.mostrarError(error);
                return EMPTY;
              }),
              finalize(() => this.consultando.set(false)),
              switchMap((datos) => of({ datos, guardada })),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ datos, guardada }) => {
        this.duracionDisponibilidad.set(datos.duracionMin);
        this.franjas.set(datos.franjas);
        if (guardada) {
          const franja = datos.franjas.find(
            (f) => f.inicio === guardada.inicio && f.barberoIds.includes(guardada.barberoId),
          );
          if (franja) {
            this.franja.set(franja);
            this.barberoId.set(guardada.barberoId);
            this.paso.set(2);
          } else {
            this.mensaje.set('La selección guardada ya no está disponible. Elija otra franja.');
            this.paso.set(1);
          }
        }
      });
  }

  inicializar(id?: number) {
    this.inicializaciones.next(id);
  }
  elegirServicio(id: number) {
    if (this.reserva() || this.enviando()) return;
    this.servicioId.set(id);
    this.paso.set(0);
    this.consultas.next(undefined);
  }
  elegirPreferencia(id: number | null) {
    if (this.enviando()) return;
    this.preferencia.set(id);
    this.paso.set(0);
    this.consultas.next(undefined);
  }
  elegirFecha(fecha: string) {
    if (this.enviando()) return;
    this.fecha.set(fecha);
    this.paso.set(1);
    this.consultas.next(undefined);
  }
  cargarFranjas() {
    this.consultas.next(undefined);
  }
  elegirFranja(franja: FranjaDto) {
    if (this.enviando() || this.consultando() || !this.franjas().includes(franja)) return;
    this.franja.set(franja);
    this.barberoId.set(franja.barberoIds[0] ?? null);
    if (this.barberoId()) this.paso.set(2);
  }
  elegirBarbero(id: number) {
    if (!this.enviando() && this.franja()?.barberoIds.includes(id)) this.barberoId.set(id);
  }
  irPaso(paso: number) {
    if (this.enviando()) return;
    if (
      paso === 0 ||
      (paso === 1 && this.pasoUnoCompleto()) ||
      (paso === 2 && this.pasoUnoCompleto() && this.pasoDosCompleto())
    )
      this.paso.set(paso);
  }
  confirmar() {
    if (!this.puedeConfirmar()) return;
    if (!this.sesion.autenticado()) {
      this.guardarSeleccion();
      void this.router.navigate(['/ingresar'], { queryParams: { returnUrl: '/reservar' } });
      return;
    }
    this.enviando.set(true);
    this.mensaje.set('');
    this.limiteReservas.set(false);
    const reserva = this.reserva();
    const inicio = this.franja()!.inicio;
    const barberoId = this.barberoId()!;
    const peticion = reserva
      ? this.reservasApi.reprogramar(reserva.id, {
          inicio,
          barberoId,
          version: reserva.version,
          ...(this.sesion.rol() === 'ADMIN' ? { motivo: this.motivo().trim() } : {}),
        })
      : this.reservasApi.crear({ servicioId: this.servicioId()!, barberoId, inicio });
    peticion
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.enviando.set(false)),
      )
      .subscribe({
        next: (datos) => {
          this.borrarSeleccion();
          this.resultado.set(datos);
          void this.router.navigate([this.sesion.rol() === 'ADMIN' ? '/agenda' : '/mis-citas'], {
            state: { avisoReserva: reserva ? 'Reserva reprogramada.' : 'Reserva creada.' },
          });
        },
        error: (error: HttpErrorResponse) => {
          const codigo = (error.error as Partial<ProblemDetail> | null)?.codigo;
          this.mostrarError(error);
          if (codigo === 'FRANJA_NO_DISPONIBLE' || codigo === 'CLIENTE_CON_RESERVA_SOLAPADA') {
            this.mensaje.set(
              codigo === 'FRANJA_NO_DISPONIBLE'
                ? 'La franja ya no está disponible. Elija otra fecha, hora o profesional.'
                : 'Tiene otra cita en ese intervalo. Elija otra franja.',
            );
            this.paso.set(1);
            this.cargarFranjas();
          }
          this.limiteReservas.set(codigo === 'LIMITE_RESERVAS_ACTIVAS');
          if (error.status === 401 && !reserva) this.guardarSeleccion();
        },
      });
  }
  private limpiarFranja() {
    this.duracionDisponibilidad.set(null);
    this.franjas.set([]);
    this.franja.set(null);
    this.barberoId.set(null);
  }
  private mostrarError(error: HttpErrorResponse) {
    const problema = error.error as Partial<ProblemDetail> | null;
    this.mensaje.set(
      problema?.detail ||
        problema?.title ||
        'No pudimos completar la solicitud. Intente nuevamente.',
    );
  }
  private guardarSeleccion() {
    try {
      const datos: SeleccionGuardada = {
        servicioId: this.servicioId()!,
        preferencia: this.preferencia(),
        fecha: this.fecha(),
        inicio: this.franja()!.inicio,
        barberoId: this.barberoId()!,
      };
      sessionStorage.setItem(CLAVE, JSON.stringify(datos));
    } catch {
      this.mensaje.set(
        'El navegador no permite conservar la selección. Vuelva a elegirla tras ingresar.',
      );
    }
  }
  private restaurar() {
    try {
      const texto = sessionStorage.getItem(CLAVE);
      if (!texto) return;
      const datos = JSON.parse(texto) as SeleccionGuardada;
      this.borrarSeleccion();
      if (
        !this.servicios().some((s) => s.id === datos.servicioId) ||
        typeof datos.fecha !== 'string' ||
        datos.fecha < fechaHoyLima() ||
        datos.fecha > fechaHoyLima(30) ||
        !/^\d{4}-\d{2}-\d{2}$/.test(datos.fecha) ||
        typeof datos.inicio !== 'string' ||
        !Number.isInteger(datos.barberoId) ||
        (datos.preferencia !== null && !this.barberos().some((b) => b.id === datos.preferencia))
      )
        return;
      this.servicioId.set(datos.servicioId);
      this.preferencia.set(datos.preferencia);
      this.fecha.set(datos.fecha);
      this.paso.set(1);
      this.cargarRestaurada(datos);
    } catch {
      this.borrarSeleccion();
    }
  }
  private cargarRestaurada(datos: SeleccionGuardada) {
    // forkJoin publica antes de finalizar: permite la consulta tras cargar el catálogo.
    this.cargando.set(false);
    this.consultas.next(datos);
  }
  private borrarSeleccion() {
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      /* Almacenamiento restringido: estado solo en memoria. */
    }
  }
}
