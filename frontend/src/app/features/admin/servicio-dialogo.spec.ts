import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ServicioDialogo } from './servicio-dialogo';

describe('Diálogo de servicio y errores por campo', () => {
  let http: HttpTestingController;
  const referencia = { close: vi.fn(), disableClose: false };
  beforeEach(() => {
    referencia.close.mockClear();
    TestBed.configureTestingModule({
      imports: [ServicioDialogo],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: null },
        { provide: MatDialogRef, useValue: referencia },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function preparar() {
    const fixture = TestBed.createComponent(ServicioDialogo);
    fixture.componentInstance.formulario.controls.nombre.setValue('Corte ficticio');
    fixture.detectChanges();
    return fixture;
  }
  it('409 NOMBRE_DUPLICADO se ve junto al nombre y permite corregir y reenviar', () => {
    const fixture = preparar();
    fixture.componentInstance.guardar();
    http
      .expectOne('/api/servicios')
      .flush(
        { codigo: 'NOMBRE_DUPLICADO', detail: 'Ya existe un servicio con ese nombre.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();
    const nombre = fixture.componentInstance.formulario.controls.nombre;
    expect(nombre.touched).toBe(true);
    expect(fixture.nativeElement.querySelector('mat-error').textContent).toContain('Ya existe');
    expect(referencia.close).not.toHaveBeenCalled();
    nombre.setValue('Otro corte');
    fixture.componentInstance.guardar();
    http.expectOne('/api/servicios').flush({ id: 4 });
    expect(referencia.close).toHaveBeenCalledWith(true);
  });
  it('400 errores[] muestra todos los campos conocidos y acumula mensajes', () => {
    const fixture = preparar();
    fixture.componentInstance.guardar();
    http.expectOne('/api/servicios').flush(
      {
        codigo: 'VALIDACION',
        detail: 'Revise los datos.',
        errores: [
          { campo: 'duracionMin', mensaje: 'Debe ser múltiplo de diez.' },
          { campo: 'precio', mensaje: 'Precio inválido.' },
          { campo: 'precio', mensaje: 'Hasta dos decimales.' },
          { campo: 'desconocido', mensaje: 'Error general.' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    expect(
      fixture.componentInstance.formulario.controls.duracionMin.getError('servidor'),
    ).toContain('múltiplo');
    expect(fixture.componentInstance.formulario.controls.precio.getError('servidor')).toBe(
      'Precio inválido. Hasta dos decimales.',
    );
    expect(fixture.nativeElement.textContent).toContain('Precio inválido.');
    expect(fixture.componentInstance.mensaje()).toBe('Revise los datos.');
  });
  it('impide enviar formulario inválido y solicitudes dobles', () => {
    const fixture = TestBed.createComponent(ServicioDialogo);
    fixture.componentInstance.guardar();
    http.expectNone('/api/servicios');
    fixture.componentInstance.formulario.controls.nombre.setValue('Corte ficticio');
    fixture.componentInstance.guardar();
    fixture.componentInstance.guardar();
    http.expectOne('/api/servicios').flush({ id: 4 });
  });
});
