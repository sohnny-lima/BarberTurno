import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { ReprogramarDialogo } from './reprogramar-dialogo';

describe('Reprogramación administrativa', () => {
  let http: HttpTestingController;
  const referencia = { close: vi.fn(), disableClose: false };
  const inicio = '2026-10-05T11:00:00-05:00';
  beforeEach(() => {
    referencia.close.mockClear();
    referencia.disableClose = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            reserva: RESERVA_PRUEBA,
            barberos: [
              { id: 3, nombre: 'Ficticio', activo: true },
              { id: 4, nombre: 'Otro ficticio', activo: true },
            ],
          },
        },
        { provide: MatDialogRef, useValue: referencia },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function crear() {
    const fixture = TestBed.createComponent(ReprogramarDialogo);
    const consulta = http.expectOne((r) => r.url === '/api/disponibilidad');
    expect(consulta.request.params.get('servicioId')).toBe('2');
    expect(consulta.request.params.get('excluirReservaId')).toBe('101');
    expect(consulta.request.params.get('barberoId')).toBe('3');
    consulta.flush({ franjas: [{ inicio, fin: '2026-10-05T11:30:00-05:00', barberoIds: [3] }] });
    fixture.componentInstance.formulario.controls.inicio.setValue(inicio);
    return fixture;
  }
  it.each(['', '    ', 'abc', 'x'.repeat(301)])('motivo inválido "%s" impide enviar', (motivo) => {
    const fixture = crear();
    fixture.componentInstance.formulario.controls.motivo.setValue(motivo);
    fixture.componentInstance.confirmar();
    http.expectNone('/api/reservas/101/reprogramacion');
    expect(fixture.componentInstance.formulario.controls.motivo.invalid).toBe(true);
  });
  it('envía franja del servidor, barbero, versión y motivo trim; bloquea doble envío y cierre', () => {
    const fixture = crear();
    fixture.componentInstance.formulario.controls.motivo.setValue('  Cambio ficticio  ');
    fixture.componentInstance.confirmar();
    fixture.componentInstance.confirmar();
    expect(referencia.disableClose).toBe(true);
    expect(fixture.componentInstance.formulario.disabled).toBe(true);
    const peticion = http.expectOne('/api/reservas/101/reprogramacion');
    expect(peticion.request.body).toEqual({
      inicio,
      barberoId: 3,
      version: 7,
      motivo: 'Cambio ficticio',
    });
    peticion.flush(RESERVA_PRUEBA);
    expect(referencia.close).toHaveBeenCalledWith('guardada');
  });
  it('cambiar profesional cancela consulta anterior y elimina selección obsoleta', () => {
    const fixture = crear();
    fixture.componentInstance.cargar();
    const anterior = http.expectOne((r) => r.url === '/api/disponibilidad');
    fixture.componentInstance.formulario.controls.barberoId.setValue(4);
    fixture.componentInstance.cargar();
    expect(anterior.cancelled).toBe(true);
    expect(fixture.componentInstance.formulario.controls.inicio.value).toBe('');
    const consulta = http.expectOne((r) => r.url === '/api/disponibilidad');
    expect(consulta.request.params.get('barberoId')).toBe('4');
    consulta.flush({ franjas: [] });
  });
  it.each(['VERSION_DESACTUALIZADA', 'FRANJA_NO_DISPONIBLE'])(
    '409 %s pide recargar agenda',
    (codigo) => {
      const fixture = crear();
      fixture.componentInstance.formulario.controls.motivo.setValue('Cambio ficticio');
      fixture.componentInstance.confirmar();
      http
        .expectOne('/api/reservas/101/reprogramacion')
        .flush({ codigo }, { status: 409, statusText: 'Conflict' });
      expect(referencia.close).toHaveBeenCalledWith('actualizada');
    },
  );
  it('422 muestra detail sin cerrar', () => {
    const fixture = crear();
    fixture.componentInstance.formulario.controls.motivo.setValue('Cambio ficticio');
    fixture.componentInstance.confirmar();
    http
      .expectOne('/api/reservas/101/reprogramacion')
      .flush({ detail: 'La cita ya empezó' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('La cita ya empezó');
    expect(referencia.close).not.toHaveBeenCalled();
    expect(fixture.componentInstance.formulario.enabled).toBe(true);
  });
});
