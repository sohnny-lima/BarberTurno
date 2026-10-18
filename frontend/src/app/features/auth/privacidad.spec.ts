import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Privacidad } from './privacidad';

describe('Aviso de privacidad', () => {
  it('conserva los párrafos, el título único y el enlace al registro', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const f = TestBed.createComponent(Privacidad);
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(f.nativeElement.querySelector('h1').textContent).toBe('Aviso de privacidad');
    expect(f.nativeElement.querySelectorAll('p')).toHaveLength(3);
    expect(f.nativeElement.textContent).toContain('Ley 29733');
    expect(f.nativeElement.textContent).toContain('No ingrese datos personales reales.');
    const enlace = f.nativeElement.querySelector('a');
    expect(enlace.textContent.trim()).toBe('Volver al registro');
    expect(enlace.getAttribute('href')).toBe('/registro');
    expect(enlace.hasAttribute('mat-button')).toBe(true);
  });
});
