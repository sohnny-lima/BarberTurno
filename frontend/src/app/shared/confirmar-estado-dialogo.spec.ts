import { MatButton } from '@angular/material/button';
import { By } from '@angular/platform-browser';
import { Subject } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { map } from 'rxjs';
import { BarberosApi } from '../core/api/barberos-api';
import { ConfirmarEstadoDatos, ConfirmarEstadoDialogo } from './confirmar-estado-dialogo';

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
  it.each([undefined, false, true])(
    'el riesgo = %s es explícito y Cerrar conserva el estilo normal tras completar',
    (riesgo) => {
      const respuesta = new Subject<number>();
      const cambiar = vi.fn(() => respuesta);
      Object.assign(TestBed.inject<ConfirmarEstadoDatos>(MAT_DIALOG_DATA), {
        nombre: 'Ficticio',
        activar: false,
        riesgo,
        boton: 'Restablecer contraseña',
        cambiar,
      });
      const f = TestBed.createComponent(ConfirmarEstadoDialogo);
      f.detectChanges();
      const accion = f.debugElement.query(By.css('app-accion-dialogo'));
      const boton = accion.query(By.directive(MatButton)).componentInstance as MatButton;
      expect(boton.appearance).toBe(riesgo ? 'outlined' : 'filled');
      const confirmar: HTMLButtonElement = accion.nativeElement.querySelector('button');
      expect(confirmar.classList.contains('riesgo')).toBe(riesgo === true);
      expect(confirmar.textContent?.trim()).toBe('Restablecer contraseña');
      expect(cambiar).not.toHaveBeenCalled();
      confirmar.click();
      f.detectChanges();
      expect(confirmar.disabled).toBe(true);
      expect(confirmar.textContent?.trim()).toBe('Guardando…');
      confirmar.click();
      expect(cambiar).toHaveBeenCalledOnce();
      respuesta.next(3);
      respuesta.complete();
      f.detectChanges();
      expect(f.nativeElement.querySelector('app-accion-dialogo')).toBeNull();
      const cerrar = f.debugElement.query(By.directive(MatButton)).componentInstance as MatButton;
      expect(cerrar.appearance).toBe('filled');
      expect(f.nativeElement.querySelector('button').textContent.trim()).toBe('Cerrar');
      f.nativeElement.querySelector('button').click();
      expect(referencia.close).toHaveBeenCalledWith(true);
    },
  );
});
