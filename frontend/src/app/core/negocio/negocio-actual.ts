import { HttpClient, HttpContext, HttpContextToken, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, filter, forkJoin, map, of, Subject, takeUntil, tap, throwError } from 'rxjs';
import { UsuarioSesionDto } from '../modelos/identidad';
import { AmbitoNegocio, EstadoNegocio, NegocioPublicoDto, Papel } from '../modelos/negocios';

export const ORIGEN_NEGOCIO = new HttpContextToken<AmbitoNegocio | null>(() => null);
export const CONSULTA_CONTEXTO = new HttpContextToken<boolean>(() => false);

/** Contexto de presentación por URL; nunca concede permisos ni conserva un negocio implícito. */
@Injectable({ providedIn: 'root' })
export class NegocioActual {
  private readonly http = inject(HttpClient);
  private secuencia = 0;
  private readonly actual = signal<AmbitoNegocio | null>(null);
  private readonly datos = signal<EstadoNegocio | null>(null);
  private readonly cambios = new Subject<void>();
  readonly cambios$ = this.cambios.asObservable();
  readonly ambito = this.actual.asReadonly();
  readonly estado = this.datos.asReadonly();
  readonly slug = computed(() => this.ambito()?.slug ?? null);
  readonly papel = computed(() => this.ambito()?.papel ?? 'PERSONAL');
  readonly usuario = computed(() => this.estado()?.sesion ?? null);
  readonly rol = computed(() => this.usuario()?.rol ?? null);
  readonly gestion = computed(() => this.rol() === 'ADMIN');
  readonly parametros = computed(() => this.estado()?.parametros ?? null);
  readonly nombre = computed(
    () => this.parametros()?.nombre ?? this.nombreRelacion() ?? this.slug(),
  );
  readonly nombreRelacion = signal<string | null>(null);
  readonly avisoSelector = signal('');

  seleccionar(slug: string | null, papel: Papel = 'PERSONAL') {
    if (this.slug() === slug && this.papel() === papel) return;
    if (slug) {
      try {
        const texto = sessionStorage.getItem('barberturno.reserva');
        const guardada = texto ? JSON.parse(texto) : null;
        if (guardada && (guardada.slug !== slug || guardada.papel !== papel)) {
          sessionStorage.removeItem('barberturno.reserva');
        }
      } catch {
        /* Almacenamiento opcional; el store vuelve a validar cualquier selección. */
      }
    }
    this.actual.set(slug ? { slug, papel, generacion: ++this.secuencia } : null);
    this.datos.set(null);
    this.nombreRelacion.set(null);
    this.cambios.next();
  }
  vigente(origen: AmbitoNegocio) {
    const actual = this.ambito();
    return (
      actual?.generacion === origen.generacion &&
      actual.slug === origen.slug &&
      actual.papel === origen.papel
    );
  }
  contexto(consulta = false) {
    const origen = this.ambito();
    if (!origen) throw new Error('La operación requiere una barbería en la ruta.');
    return new HttpContext().set(ORIGEN_NEGOCIO, origen).set(CONSULTA_CONTEXTO, consulta);
  }
  /** Cada instancia de API de la vista conserva su origen, incluso antes de suscribirse. */
  cliente() {
    const origen = this.ambito();
    if (!origen) throw new Error('La operación requiere una barbería en la ruta.');
    return {
      url: (recurso: string) => '/api/n/' + origen.slug + recurso,
      contexto: () => new HttpContext().set(ORIGEN_NEGOCIO, origen),
    };
  }
  url(recurso: string) {
    const slug = this.slug();
    if (!slug) throw new Error('La operación requiere una barbería en la ruta.');
    return '/api/n/' + slug + recurso;
  }
  ruta(seccion: string) {
    return '/n/' + this.slug() + '/' + seccion;
  }
  enlace(seccion: string) {
    return this.ruta(seccion) + (this.papel() === 'CLIENTE' ? '?papel=cliente' : '');
  }
  cargar() {
    const origen = this.ambito()!;
    const contexto = () => this.contexto(true);
    this.datos.set({ tipo: 'cargando', sesion: null, parametros: null });
    const sesion = this.http
      .get<UsuarioSesionDto>(this.url('/sesion'), { context: contexto() })
      .pipe(
        catchError((error: HttpErrorResponse) =>
          error.status === 401 ? of(null) : throwError(() => error),
        ),
      );
    const negocio = this.http
      .get<NegocioPublicoDto>(this.url('/negocio'), { context: contexto() })
      .pipe(
        catchError((error: HttpErrorResponse) =>
          error.status === 404 ? of(null) : throwError(() => error),
        ),
      );
    return forkJoin({ sesion, parametros: negocio }).pipe(
      map(({ sesion, parametros }): EstadoNegocio => ({
        tipo: parametros ? 'disponible' : 'suspendido',
        sesion,
        parametros,
      })),
      catchError((error: HttpErrorResponse) =>
        of<EstadoNegocio>({
          tipo: error.status === 404 ? 'inexistente' : 'error',
          sesion: null,
          parametros: null,
        }),
      ),
      takeUntil(this.cambios$),
      filter(() => this.vigente(origen)),
      tap((estado) => this.datos.set(estado)),
    );
  }
  actualizarSesion(usuario: UsuarioSesionDto | null) {
    this.datos.update((datos) => (datos ? { ...datos, sesion: usuario } : datos));
  }
  limpiar() {
    this.actual.set(null);
    this.datos.set(null);
    this.nombreRelacion.set(null);
    this.avisoSelector.set('');
    this.cambios.next();
    try {
      sessionStorage.removeItem('barberturno.reserva');
      localStorage.removeItem('barberturno.ultima-barberia');
    } catch {
      /* Almacenamiento opcional. */
    }
  }
}
