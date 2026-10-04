import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { AvisosService } from '../../core/notificaciones/avisos-service';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { MisCitas } from './mis-citas';
import { ResultadoCancelacion } from './cancelar-dialogo';

describe('Mis citas y filtros', () => {
  let http: HttpTestingController;
  let resultado: Subject<ResultadoCancelacion | undefined>;
  const actualizar = vi.fn();
  const snackbar = { open: vi.fn() };
  beforeEach(() => {
    actualizar.mockClear();
    snackbar.open.mockClear();
    resultado = new Subject();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
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
});
