import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Registro } from './registro';

describe('Formulario de registro', () => {
  let fixture: ComponentFixture<Registro>;
  let pagina: Registro;
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Registro],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(Registro);
    pagina = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());
  function rellenar() {
    pagina.formulario.setValue({
      nombre: 'Cliente ficticio',
      correo: 'registro@ejemplo.test',
      telefono: '999000000',
      password: 'Ficticia12345',
      confirmacion: 'Ficticia12345',
      aceptaPrivacidad: true,
    });
  }
  it('exige consentimiento antes de enviar', () => {
    rellenar();
    pagina.formulario.controls.aceptaPrivacidad.setValue(false);
    pagina.enviar();
    expect(pagina.formulario.controls.aceptaPrivacidad.hasError('required')).toBe(true);
    http.expectNone('/api/auth/registro');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Debe aceptar el aviso de privacidad.');
  });
  it('exige confirmación coincidente y asocia el error al campo', () => {
    rellenar();
    pagina.formulario.controls.confirmacion.setValue('Otra123456');
    pagina.enviar();
    expect(pagina.formulario.hasError('confirmacion')).toBe(true);
    http.expectNone('/api/auth/registro');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#error-confirmacion')?.textContent).toContain(
      'Las contraseñas deben coincidir.',
    );
    expect(
      fixture.nativeElement
        .querySelector('[formControlName=confirmacion]')
        .getAttribute('aria-describedby'),
    ).toContain('error-confirmacion');
  });
  it('muestra errores del servidor por campo y permite corregirlos', async () => {
    rellenar();
    pagina.enviar();
    http.expectOne('/api/auth/registro').flush(
      {
        detail: 'Revise los datos.',
        errores: [
          { campo: 'telefono', mensaje: 'Debe tener nueve dígitos.' },
          { campo: 'password', mensaje: 'Debe incluir una mayúscula.' },
          { campo: 'password', mensaje: 'Debe incluir un número.' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(pagina.formulario.controls.telefono.getError('servidor')).toBe(
      'Debe tener nueve dígitos.',
    );
    expect(fixture.nativeElement.querySelectorAll('mat-error').length).toBeGreaterThanOrEqual(2);
    expect(fixture.nativeElement.textContent).toContain(
      'Debe incluir una mayúscula. Debe incluir un número.',
    );
    pagina.formulario.controls.telefono.setValue('999000001');
    pagina.formulario.controls.password.setValue('Corregida12345');
    pagina.formulario.controls.confirmacion.setValue('Corregida12345');
    expect(pagina.formulario.valid).toBe(true);
    expect(pagina.cargando()).toBe(false);
  });
  it('envía solo el DTO, evita doble envío y navega tras el 201', () => {
    rellenar();
    pagina.enviar();
    pagina.enviar();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBe(true);
    const peticion = http.expectOne('/api/auth/registro');
    expect(peticion.request.body).not.toHaveProperty('confirmacion');
    expect(peticion.request.body.aceptaPrivacidad).toBe(true);
    peticion.flush(
      {
        id: 1,
        nombre: 'Cliente ficticio',
        correo: 'registro@ejemplo.test',
        rol: 'CLIENTE',
        debeCambiarPassword: false,
      },
      { status: 201, statusText: 'Created' },
    );
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/reservar');
    expect(pagina.cargando()).toBe(false);
  });
  it('correo duplicado aparece junto al correo', () => {
    rellenar();
    pagina.enviar();
    http
      .expectOne('/api/auth/registro')
      .flush(
        { codigo: 'CORREO_DUPLICADO', detail: 'El correo ya está registrado.' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(pagina.formulario.controls.correo.getError('servidor')).toBe(
      'El correo ya está registrado.',
    );
  });
});
