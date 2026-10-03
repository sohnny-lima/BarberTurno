import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { BarberoDto } from '../../core/modelos/catalogo';
import { BarberoDialogo } from './barbero-dialogo';
import { PasswordTemporalDialogo } from './password-temporal-dialogo';

describe('Alta y edición de barberos', () => {
  let http: HttpTestingController;
  let cerrado: Subject<boolean>;
  let referencia: {
    close: ReturnType<typeof vi.fn>;
    afterClosed: () => Subject<boolean>;
    disableClose: boolean;
  };
  const dialogos = { open: vi.fn() };
  function preparar(barbero: BarberoDto | null = null) {
    cerrado = new Subject<boolean>();
    referencia = { close: vi.fn(), afterClosed: () => cerrado, disableClose: false };
    TestBed.configureTestingModule({
      imports: [BarberoDialogo],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: barbero },
        { provide: MatDialogRef, useValue: referencia },
        { provide: MatDialog, useValue: dialogos },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogos });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(BarberoDialogo);
    fixture.detectChanges();
    return fixture;
  }
  function nueva() {
    const fixture = preparar();
    const controles = fixture.componentInstance.formulario.controls;
    controles.nombre.setValue('Barbero ficticio');
    controles.correo.setValue('personal@ejemplo.test');
    controles.especialidad.setValue('Corte y barba');
    fixture.componentInstance.generar();
    return fixture;
  }
  beforeEach(() => dialogos.open.mockClear());
  afterEach(() => http.verify());
  it('crea una cuenta y entrega la contraseña una sola vez después de cerrar el alta', () => {
    const fixture = nueva();
    const password = fixture.componentInstance.formulario.controls.passwordTemporal.value;
    fixture.componentInstance.guardar();
    fixture.componentInstance.guardar();
    const peticion = http.expectOne('/api/barberos');
    expect(peticion.request.body).toEqual({
      nombre: 'Barbero ficticio',
      correo: 'personal@ejemplo.test',
      telefono: null,
      especialidad: 'Corte y barba',
      passwordTemporal: password,
    });
    expect(referencia.disableClose).toBe(true);
    peticion.flush({ id: 17, activo: true });
    expect(fixture.componentInstance.formulario.controls.passwordTemporal.value).toBe('');
    expect(referencia.close).toHaveBeenCalledWith(true);
    expect(dialogos.open).not.toHaveBeenCalled();
    cerrado.next(true);
    cerrado.next(true);
    expect(dialogos.open).toHaveBeenCalledTimes(1);
    expect(dialogos.open).toHaveBeenCalledWith(
      PasswordTemporalDialogo,
      expect.objectContaining({ data: password }),
    );
  });
  it('vincula ADMIN con la variante excluyente y limpia la contraseña al cambiar de modo', () => {
    const fixture = nueva();
    const controles = fixture.componentInstance.formulario.controls;
    controles.modo.setValue('vincular');
    fixture.componentInstance.ajustarModo();
    controles.usuarioId.setValue(8);
    fixture.detectChanges();
    expect(controles.passwordTemporal.value).toBe('');
    expect(fixture.nativeElement.textContent).toContain('T-31');
    fixture.componentInstance.guardar();
    const peticion = http.expectOne('/api/barberos');
    expect(peticion.request.body).toEqual({ usuarioId: 8, especialidad: 'Corte y barba' });
    peticion.flush({ id: 17 });
    cerrado.next(true);
    expect(dialogos.open).not.toHaveBeenCalled();
  });
  it('edita únicamente nombre, teléfono y especialidad sin pedir contraseña', () => {
    const fixture = preparar({
      id: 17,
      activo: true,
      nombre: 'Ficticio',
      especialidad: 'Cortes',
      correo: 'personal@ejemplo.test',
    });
    fixture.componentInstance.formulario.controls.telefono.setValue('999000017');
    fixture.componentInstance.guardar();
    const peticion = http.expectOne('/api/barberos/17');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({
      nombre: 'Ficticio',
      telefono: '999000017',
      especialidad: 'Cortes',
    });
    peticion.flush({ id: 17 });
    cerrado.next(true);
    expect(dialogos.open).not.toHaveBeenCalled();
  });
  it('muestra CORREO_DUPLICADO en el campo y permite corregirlo sin entregar contraseña en el fallo', () => {
    const fixture = nueva();
    fixture.componentInstance.guardar();
    http
      .expectOne('/api/barberos')
      .flush(
        { codigo: 'CORREO_DUPLICADO', detail: 'Ya existe una cuenta con ese correo.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();
    expect(fixture.componentInstance.formulario.controls.correo.getError('servidor')).toContain(
      'Ya existe',
    );
    expect(fixture.nativeElement.querySelector('mat-error').textContent).toContain('ese correo');
    expect(dialogos.open).not.toHaveBeenCalled();
    expect(referencia.close).not.toHaveBeenCalled();
    fixture.componentInstance.formulario.controls.correo.setValue('otro@ejemplo.test');
    fixture.componentInstance.guardar();
    http.expectOne('/api/barberos').flush({ id: 17 });
    expect(referencia.close).toHaveBeenCalledWith(true);
  });
  it('explica el límite de diez activos sin cerrar el formulario', () => {
    const fixture = nueva();
    fixture.componentInstance.guardar();
    http.expectOne('/api/barberos').flush(
      {
        codigo: 'LIMITE_BARBEROS_ACTIVOS',
        detail: 'Como máximo puede haber 10 barberos activos.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      '10 barberos activos',
    );
    expect(referencia.disableClose).toBe(false);
    expect(referencia.close).not.toHaveBeenCalled();
  });
  it('rechaza un ID inválido y vuelve a exigir los datos de cuenta al cambiar de modo', () => {
    const fixture = preparar();
    const controles = fixture.componentInstance.formulario.controls;
    controles.modo.setValue('vincular');
    fixture.componentInstance.ajustarModo();
    controles.especialidad.setValue('Cortes');
    controles.usuarioId.setValue(1.5);
    fixture.componentInstance.guardar();
    http.expectNone('/api/barberos');
    controles.usuarioId.setValue(8);
    expect(fixture.componentInstance.formulario.valid).toBe(true);
    controles.modo.setValue('nueva');
    fixture.componentInstance.ajustarModo();
    expect(fixture.componentInstance.formulario.invalid).toBe(true);
    fixture.componentInstance.guardar();
    http.expectNone('/api/barberos');
  });
});
