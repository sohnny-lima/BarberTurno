import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SesionService } from '../../core/auth/sesion-service';
import { CambiarPassword } from './cambiar-password';

describe('Formulario de cambio de contraseña', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('muestra la ayuda de contraseña conforme a RN-25', async () => {
    await TestBed.configureTestingModule({
      imports: [CambiarPassword],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(CambiarPassword);
    fixture.detectChanges();
    const ayuda = fixture.nativeElement.querySelector('.ayuda') as HTMLElement;
    expect(ayuda.textContent?.trim()).toBe(
      'De 8 a 72 caracteres, máximo 72 bytes, con letra y dígito.',
    );
  });
  it.each([false, true])(
    'mantiene aviso y título móvil según cambio obligatorio = %s',
    (obligatorio) => {
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      const sesion = TestBed.inject(SesionService);
      sesion.cargar().subscribe();
      TestBed.inject(HttpTestingController).expectOne('/api/auth/sesion').flush({
        id: 12,
        nombre: 'Cliente ficticio',
        correo: 'clave@ejemplo.test',
        rol: 'CLIENTE',
        debeCambiarPassword: obligatorio,
      });
      const f = TestBed.createComponent(CambiarPassword);
      f.detectChanges();
      expect(f.nativeElement.querySelectorAll('h1')).toHaveLength(1);
      expect(!!f.nativeElement.querySelector('.titulo-movil')).toBe(!obligatorio);
      const aviso = f.nativeElement.querySelector('[role=status]');
      if (obligatorio) {
        expect(aviso.textContent).toBe('Su contraseña es temporal. Cámbiela para continuar.');
        expect(aviso.classList.contains('aviso')).toBe(true);
      } else expect(aviso).toBeNull();
      expect(
        f.nativeElement
          .querySelector('[formControlName=passwordNueva]')
          .getAttribute('aria-describedby'),
      ).toContain('ayuda-password');
      expect(
        f.nativeElement
          .querySelector('[formControlName=passwordActual]')
          .getAttribute('autocomplete'),
      ).toBe('current-password');
      expect(
        f.nativeElement
          .querySelector('[formControlName=passwordNueva]')
          .getAttribute('autocomplete'),
      ).toBe('new-password');
    },
  );
  it('conserva un ancho de lectura de 720 px alineado a la izquierda', () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const f = TestBed.createComponent(CambiarPassword);
    f.detectChanges();
    const estilo = getComputedStyle(f.nativeElement.querySelector('.pagina-cuenta'));
    expect(estilo.maxWidth).toBe('720px');
    expect(estilo.marginInline).toBe('0px');
  });
});
