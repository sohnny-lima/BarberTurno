import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { TransicionDialogo } from './transicion-dialogo';

describe('Confirmación de una transición', () => {
  let http: HttpTestingController;
  const referencia = { close: vi.fn(), disableClose: false };
  beforeEach(() => {
    referencia.close.mockClear();
    referencia.disableClose = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { reserva: RESERVA_PRUEBA, estado: 'EN_ATENCION' } },
        { provide: MatDialogRef, useValue: referencia },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('envía destino y versión, evita doble envío y bloquea el cierre', () => {
    const fixture = TestBed.createComponent(TransicionDialogo);
    fixture.componentInstance.confirmar();
    fixture.componentInstance.confirmar();
    expect(referencia.disableClose).toBe(true);
    const peticion = http.expectOne('/api/reservas/101/transiciones');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ estado: 'EN_ATENCION', version: 7 });
    peticion.flush({ ...RESERVA_PRUEBA, estado: 'EN_ATENCION', version: 8 });
    expect(referencia.close).toHaveBeenCalledWith('guardada');
  });
  it.each(['VERSION_DESACTUALIZADA', 'TRANSICION_INVALIDA'])('409 %s pide recarga', (codigo) => {
    const fixture = TestBed.createComponent(TransicionDialogo);
    fixture.componentInstance.confirmar();
    http
      .expectOne('/api/reservas/101/transiciones')
      .flush({ codigo }, { status: 409, statusText: 'Conflict' });
    expect(referencia.close).toHaveBeenCalledWith('actualizada');
    expect(referencia.disableClose).toBe(false);
  });
  it('422 conserva diálogo y muestra el motivo del servidor', () => {
    const fixture = TestBed.createComponent(TransicionDialogo);
    fixture.componentInstance.confirmar();
    http
      .expectOne('/api/reservas/101/transiciones')
      .flush({ detail: 'Fuera de ventana' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Fuera de ventana');
    expect(referencia.close).not.toHaveBeenCalled();
    expect(fixture.componentInstance.guardando()).toBe(false);
  });
});
