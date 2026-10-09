import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
});
