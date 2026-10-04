import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Ingresar } from './ingresar';

describe('Ingreso desde la reserva guiada', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());
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
