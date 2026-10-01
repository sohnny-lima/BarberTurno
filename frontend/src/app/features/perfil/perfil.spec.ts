import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SesionService } from '../../core/auth/sesion-service';
import { Perfil } from './perfil';

describe('Perfil propio', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Perfil],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const perfil = {
    id: 12,
    nombre: 'Cliente ficticio',
    correo: 'perfil@ejemplo.test',
    telefono: '999000000',
    rol: 'CLIENTE',
  };
  it('carga el perfil con correo de solo lectura y guarda solo nombre y teléfono', () => {
    const sesion = TestBed.inject(SesionService);
    sesion.cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush({ ...perfil, debeCambiarPassword: false });
    const fixture = TestBed.createComponent(Perfil);
    const pagina = fixture.componentInstance;
    http.expectOne('/api/perfil').flush(perfil);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[formControlName=correo]').readOnly).toBe(true);
    pagina.formulario.controls.nombre.setValue('Nombre ficticio actualizado');
    pagina.enviar();
    pagina.enviar();
    const peticion = http.expectOne('/api/perfil');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({
      nombre: 'Nombre ficticio actualizado',
      telefono: perfil.telefono,
    });
    peticion.flush({ ...perfil, nombre: 'Nombre ficticio actualizado' });
    expect(sesion.usuario()?.nombre).toBe('Nombre ficticio actualizado');
    expect(pagina.mensaje()).toBe('Perfil guardado.');
    expect(pagina.cargando()).toBe(false);
  });
  it('muestra el teléfono obligatorio devuelto por el servidor', () => {
    const pagina = TestBed.createComponent(Perfil).componentInstance;
    http.expectOne('/api/perfil').flush(perfil);
    pagina.formulario.controls.telefono.setValue('');
    pagina.enviar();
    http.expectOne('/api/perfil').flush(
      {
        detail: 'Revise los datos.',
        errores: [{ campo: 'telefono', mensaje: 'El teléfono es obligatorio para el cliente.' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    expect(pagina.formulario.controls.telefono.getError('servidor')).toBe(
      'El teléfono es obligatorio para el cliente.',
    );
  });
});
