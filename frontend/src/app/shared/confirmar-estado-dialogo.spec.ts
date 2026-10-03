import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { map } from 'rxjs';
import { BarberosApi } from '../core/api/barberos-api';
import { ConfirmarEstadoDialogo } from './confirmar-estado-dialogo';

describe('Confirmación de desactivación de barbero', () => {
  let http: HttpTestingController;
  const referencia = { close: vi.fn(), disableClose: false };
  beforeEach(() => {
    referencia.close.mockClear();
    TestBed.configureTestingModule({
      imports: [ConfirmarEstadoDialogo],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: referencia },
        {
          provide: MAT_DIALOG_DATA,
          useFactory: () => {
            const api = TestBed.inject(BarberosApi);
            return {
              nombre: 'Barbero ficticio',
              activar: false,
              cambiar: () =>
                api.cambiarEstado(17, false).pipe(map((dato) => dato.reservasFuturasVigentes)),
            };
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('solo envía tras confirmar y muestra el aviso devuelto por el servidor', () => {
    const fixture = TestBed.createComponent(ConfirmarEstadoDialogo);
    fixture.detectChanges();
    http.expectNone('/api/barberos/17/estado');
    fixture.componentInstance.confirmar();
    fixture.componentInstance.confirmar();
    const peticion = http.expectOne('/api/barberos/17/estado');
    expect(peticion.request.body).toEqual({ activo: false });
    peticion.flush({ barbero: { id: 17, activo: false }, reservasFuturasVigentes: 3 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Tiene 3 reservas futuras: gestiónelas en la agenda',
    );
    expect(referencia.close).not.toHaveBeenCalled();
    expect(referencia.disableClose).toBe(true);
    const boton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    boton.click();
    expect(referencia.close).toHaveBeenCalledWith(true);
  });
  it('con cero reservas cierra al terminar', () => {
    const fixture = TestBed.createComponent(ConfirmarEstadoDialogo);
    fixture.componentInstance.confirmar();
    http
      .expectOne('/api/barberos/17/estado')
      .flush({ barbero: { id: 17 }, reservasFuturasVigentes: 0 });
    expect(referencia.close).toHaveBeenCalledWith(true);
  });
  it('muestra límite de activos y permite reintentar tras un 422', () => {
    const fixture = TestBed.createComponent(ConfirmarEstadoDialogo);
    fixture.componentInstance.confirmar();
    http.expectOne('/api/barberos/17/estado').flush(
      {
        codigo: 'LIMITE_BARBEROS_ACTIVOS',
        detail: 'Como máximo puede haber 10 barberos activos.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('10 barberos activos');
    expect(fixture.componentInstance.guardando()).toBe(false);
    expect(referencia.close).not.toHaveBeenCalled();
  });
});
