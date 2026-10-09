import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('App', () => {
  it('muestra BarberTurno y el formulario en la ruta de ingreso', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create('/ingresar');
    expect(harness.routeNativeElement?.querySelector('.marca')?.textContent).toContain(
      'BarberTurno',
    );
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Iniciar sesión');
  });
});
