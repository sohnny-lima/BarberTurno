import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { CancelarDialogo } from './cancelar-dialogo';

describe('Cancelación de una cita', () => {
  let http: HttpTestingController;
  const referencia = { close: vi.fn(), disableClose: false };
  beforeEach(() => {
    referencia.close.mockClear();
    referencia.disableClose = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: RESERVA_PRUEBA },
        { provide: MatDialogRef, useValue: referencia },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it.each(['', '  Cambio de planes  '])(
    'envía la versión y motivo opcional "%s", sin duplicar',
    (motivo) => {
      const fixture = TestBed.createComponent(CancelarDialogo);
      fixture.componentInstance.formulario.controls.motivo.setValue(motivo);
      fixture.componentInstance.confirmar();
      fixture.componentInstance.confirmar();
      const solicitud = http.expectOne('/api/reservas/101/cancelacion');
      expect(solicitud.request.method).toBe('POST');
      expect(solicitud.request.body).toEqual({
        version: 7,
        ...(motivo ? { motivo: 'Cambio de planes' } : {}),
      });
      expect(referencia.disableClose).toBe(true);
      solicitud.flush({ ...RESERVA_PRUEBA, estado: 'CANCELADA', version: 8 });
      expect(referencia.close).toHaveBeenCalledWith('cancelada');
    },
  );
  it('rechaza motivos de más de 300 caracteres sin solicitar cancelación', () => {
    const fixture = TestBed.createComponent(CancelarDialogo);
    fixture.componentInstance.formulario.controls.motivo.setValue('a'.repeat(301));
    fixture.componentInstance.confirmar();
    http.expectNone('/api/reservas/101/cancelacion');
    expect(fixture.componentInstance.formulario.invalid).toBe(true);
  });
  it('señala al listado la necesidad de recargar por 409 de versión', () => {
    const fixture = TestBed.createComponent(CancelarDialogo);
    fixture.componentInstance.confirmar();
    http
      .expectOne('/api/reservas/101/cancelacion')
      .flush({ codigo: 'VERSION_DESACTUALIZADA' }, { status: 409, statusText: 'Conflict' });
    expect(referencia.close).toHaveBeenCalledWith('actualizada');
  });
  it('muestra el detail del 422 y permite reintentar sin cerrar', () => {
    const fixture = TestBed.createComponent(CancelarDialogo);
    fixture.componentInstance.confirmar();
    http
      .expectOne('/api/reservas/101/cancelacion')
      .flush(
        { codigo: 'FUERA_DE_POLITICA', detail: 'Faltan menos de dos horas para el inicio.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Faltan menos de dos horas para el inicio.',
    );
    expect(fixture.componentInstance.guardando()).toBe(false);
    expect(referencia.close).not.toHaveBeenCalled();
  });
});
