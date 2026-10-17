import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Ingresar } from './ingresar';

describe('Ingreso desde la reserva guiada', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('conserva el título visible, autocompletado, returnUrl y alerta al fallar', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ returnUrl: '/reservar?reprogramar=7' }),
            },
          },
        },
      ],
    });
    const f = TestBed.createComponent(Ingresar);
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(f.nativeElement.querySelector('.titulo-movil')).toBeNull();
    expect(
      f.nativeElement.querySelector('[formControlName=correo]').getAttribute('autocomplete'),
    ).toBe('username');
    expect(
      f.nativeElement.querySelector('[formControlName=password]').getAttribute('autocomplete'),
    ).toBe('current-password');
    expect(f.nativeElement.querySelector('a').getAttribute('href')).toBe(
      '/registro?returnUrl=%2Freservar%3Freprogramar%3D7',
    );
    f.componentInstance.formulario.setValue({
      correo: 'acceso@ejemplo.test',
      password: 'Ficticia12345',
    });
    f.componentInstance.enviar();
    f.detectChanges();
    expect(f.nativeElement.querySelector('form').getAttribute('aria-busy')).toBe('true');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush({ detail: 'Credenciales inválidas.' }, { status: 401, statusText: 'Unauthorized' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain(
      'Credenciales inválidas.',
    );
    expect(f.nativeElement.querySelector('button[type=submit]').disabled).toBe(false);
  });
  it.each(['/reservar', '/reservar?reprogramar=7', 'https://ejemplo.test', '//ejemplo.test'])(
    'acepta solo rutas internas al volver desde %s',
    (returnUrl) => {
      TestBed.configureTestingModule({
        imports: [Ingresar],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([]),
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({ returnUrl }) } },
          },
        ],
      });
      const router = TestBed.inject(Router);
      const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      const f = TestBed.createComponent(Ingresar);
      f.componentInstance.formulario.setValue({
        correo: 't26@ejemplo.test',
        password: 'Ficticia26!',
      });
      f.componentInstance.enviar();
      TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({
        id: 1,
        nombre: 'Ficticio',
        correo: 't26@ejemplo.test',
        rol: 'CLIENTE',
        debeCambiarPassword: false,
      });
      expect(navegar).toHaveBeenCalledWith(
        returnUrl.startsWith('/reservar') ? returnUrl : '/reservar',
      );
    },
  );
});
