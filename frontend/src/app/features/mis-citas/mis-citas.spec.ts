import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { BehaviorSubject } from 'rxjs';
import { BreakpointObserver } from '@angular/cdk/layout';
import { AvisosService } from '../../core/notificaciones/avisos-service';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { MisCitas } from './mis-citas';
import { ResultadoCancelacion } from './cancelar-dialogo';
import { TituloPagina } from '../../shared/titulo-pagina';

registerLocaleData(localeEsPe);

describe('Mis citas y filtros', () => {
  let http: HttpTestingController;
  let resultado: Subject<ResultadoCancelacion | undefined>;
  let movil: BehaviorSubject<{ matches: boolean }>;
  const actualizar = vi.fn();
  const snackbar = { open: vi.fn() };
  beforeEach(() => {
    actualizar.mockClear();
    snackbar.open.mockClear();
    resultado = new Subject();
    movil = new BehaviorSubject<{ matches: boolean }>({ matches: false });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BreakpointObserver, useValue: { observe: () => movil } },
        { provide: AvisosService, useValue: { noLeidas: signal(2), actualizar } },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => resultado })) } },
        { provide: MatSnackBar, useValue: snackbar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function crear() {
    const fixture = TestBed.createComponent(MisCitas);
    const inicial = http.expectOne((r) => r.url === '/api/reservas/mias');
    expect(inicial.request.params.get('desde')).toBe(fechaHoyLima());
    inicial.flush({
      contenido: [RESERVA_PRUEBA],
      totalElementos: 21,
      pagina: 0,
      tamano: 10,
      totalPaginas: 3,
    });
    return fixture;
  }
  it('valida desde > hasta sin llamar a la API y lo explica en el formulario', () => {
    const fixture = crear();
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/notificaciones')
      .flush({ contenido: [], totalElementos: 0 });
    fixture.componentInstance.formulario.patchValue({ desde: '2026-10-08', hasta: '2026-10-07' });
    fixture.componentInstance.filtrar();
    fixture.detectChanges();
    http.expectNone((r) => r.url === '/api/reservas/mias');
    expect(fixture.nativeElement.textContent).toContain(
      'La fecha Desde no puede ser posterior a Hasta.',
    );
  });
  it('historial termina ayer en Lima y pagina por servidor', () => {
    const fixture = crear();
    fixture.componentInstance.cambiarTab(1);
    const solicitud = http.expectOne((r) => r.url === '/api/reservas/mias');
    expect(solicitud.request.params.get('hasta')).toBe(fechaHoyLima(-1));
    expect(solicitud.request.params.has('desde')).toBe(false);
    solicitud.flush({ contenido: [], totalElementos: 21 });
    fixture.componentInstance.paginar({ pageIndex: 2, pageSize: 10, length: 21 });
    const pagina = http.expectOne((r) => r.url === '/api/reservas/mias');
    expect(pagina.request.params.get('pagina')).toBe('2');
    pagina.flush({ contenido: [], totalElementos: 21 });
  });
  it('envía estado y fechas iguales válidas y reinicia la página al filtrar', () => {
    const fixture = crear();
    const fecha = fechaHoyLima(1);
    fixture.componentInstance.formulario.patchValue({
      desde: fecha,
      hasta: fecha,
      estado: 'CONFIRMADA',
    });
    fixture.componentInstance.pagina.set(3);
    fixture.componentInstance.filtrar();
    const solicitud = http.expectOne((r) => r.url === '/api/reservas/mias');
    expect(solicitud.request.params.get('desde')).toBe(fecha);
    expect(solicitud.request.params.get('hasta')).toBe(fecha);
    expect(solicitud.request.params.get('estado')).toBe('CONFIRMADA');
    expect(solicitud.request.params.get('pagina')).toBe('0');
    solicitud.flush({ contenido: [], totalElementos: 0 });
  });
  it('evita consultas fuera del periodo elegido', () => {
    const fixture = crear();
    fixture.componentInstance.formulario.patchValue({ hasta: fechaHoyLima(-1) });
    fixture.componentInstance.filtrar();
    http.expectNone((r) => r.url === '/api/reservas/mias');
    expect(fixture.componentInstance.filas()).toEqual([]);
  });
  it.each(['actualizada', 'cancelada'] as const)(
    'recarga reservas y avisos tras %s y muestra mensaje',
    (valor) => {
      const fixture = crear();
      fixture.componentInstance.cancelar(RESERVA_PRUEBA);
      resultado.next(valor);
      http
        .expectOne((r) => r.url === '/api/reservas/mias')
        .flush({ contenido: [], totalElementos: 0 });
      expect(actualizar).toHaveBeenCalledOnce();
      expect(fixture.componentInstance.revisionAvisos()).toBe(1);
      expect(snackbar.open).toHaveBeenCalledWith(
        expect.stringContaining(valor === 'actualizada' ? 'La cita cambió' : 'cancelada'),
        'Cerrar',
        { duration: 6000 },
      );
    },
  );
  it('descartar el diálogo conserva los datos sin solicitar nada', () => {
    const fixture = crear();
    fixture.componentInstance.cancelar(RESERVA_PRUEBA);
    resultado.next(undefined);
    http.expectNone((r) => r.url === '/api/reservas/mias');
    expect(actualizar).not.toHaveBeenCalled();
  });
  it('cancela una consulta anterior cuando cambian los filtros', () => {
    const fixture = crear();
    fixture.componentInstance.filtrar();
    const anterior = http.expectOne((r) => r.url === '/api/reservas/mias');
    fixture.componentInstance.formulario.controls.estado.setValue('CANCELADA');
    fixture.componentInstance.filtrar();
    expect(anterior.cancelled).toBe(true);
    http
      .expectOne((r) => r.url === '/api/reservas/mias')
      .flush({ contenido: [], totalElementos: 0 });
  });
  it('muestra vacío con foto decorativa y enlace, sin paginador ni avisos incrustados en móvil', () => {
    movil.next({ matches: true });
    const fixture = crear();
    fixture.componentInstance.filas.set([]);
    fixture.componentInstance.total.set(0);
    fixture.detectChanges();
    const imagen = fixture.nativeElement.querySelector('.vacio img');
    expect(imagen.getAttribute('src')).toBe('/fotos/sillon-luz.webp');
    expect(imagen.getAttribute('alt')).toBe('');
    expect(imagen.getAttribute('loading')).toBe('lazy');
    expect(fixture.nativeElement.textContent).toContain('No tiene citas próximas');
    expect(fixture.nativeElement.querySelector('.vacio a').getAttribute('href')).toBe('/reservar');
    expect(fixture.nativeElement.querySelector('mat-paginator')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-avisos-panel')).toBeNull();
    http.expectNone((r) => r.url === '/api/notificaciones');
  });
  it('pagina solo cuando el total supera el tamaño y no ofrece selector de tamaño', () => {
    movil.next({ matches: true });
    const fixture = crear();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-paginator')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('mat-paginator mat-select')).toBeNull();
    fixture.componentInstance.total.set(10);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-paginator')).toBeNull();
  });
  it('abre y cierra filtros con aria-expanded sin cambiar sus valores ni consultar', () => {
    movil.next({ matches: true });
    const fixture = crear();
    fixture.detectChanges();
    const boton = fixture.nativeElement.querySelector('.abrir-filtros') as HTMLButtonElement;
    expect(boton.textContent).toContain('Filtros por fecha y estado');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.getAttribute('aria-controls')).toBe('filtros-citas');
    fixture.componentInstance.formulario.controls.estado.setValue('CONFIRMADA');
    boton.click();
    fixture.detectChanges();
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(
      fixture.nativeElement.querySelector('#filtros-citas').classList.contains('abiertos'),
    ).toBe(true);
    boton.click();
    fixture.detectChanges();
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.componentInstance.formulario.controls.estado.value).toBe('CONFIRMADA');
    http.expectNone((r) => r.url === '/api/reservas/mias');
  });
  it('registra un título único, lo limpia y muestra tique solo en Próximas', () => {
    movil.next({ matches: true });
    const fixture = crear();
    fixture.detectChanges();
    expect(TestBed.inject(TituloPagina).texto()).toBe('Mis citas');
    expect(fixture.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Todas las horas corresponden a Lima.');
    expect(fixture.nativeElement.querySelector('article.tique')).not.toBeNull();
    fixture.componentInstance.historial.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('article.tique')).toBeNull();
    expect(fixture.nativeElement.querySelector('article.fila-cita')).not.toBeNull();
    fixture.destroy();
    expect(TestBed.inject(TituloPagina).texto()).toBeNull();
  });
  it('presenta esqueleto y alerta con reintento que conserva los filtros', () => {
    movil.next({ matches: true });
    const fixture = crear();
    fixture.componentInstance.formulario.controls.estado.setValue('CONFIRMADA');
    fixture.componentInstance.filtrar();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-reserva-esqueleto')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('article')).toBeNull();
    http
      .expectOne((r) => r.url === '/api/reservas/mias')
      .flush({ detail: 'Intente nuevamente.' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Intente nuevamente.',
    );
    expect(fixture.nativeElement.querySelector('.vacio')).toBeNull();
    (fixture.nativeElement.querySelector('.alerta button') as HTMLButtonElement).click();
    const reintento = http.expectOne((r) => r.url === '/api/reservas/mias');
    expect(reintento.request.params.get('estado')).toBe('CONFIRMADA');
    reintento.flush({ contenido: [], totalElementos: 0 });
  });
});
