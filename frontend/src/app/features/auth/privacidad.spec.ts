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
    expect(f.nativeElement.querySelectorAll('p')).toHaveLength(6);
    expect(f.nativeElement.textContent).toContain('Twilio y Meta (WhatsApp)');
    expect(f.nativeElement.textContent).toContain('Responsable del tratamiento: Sohnny Lima');
    expect(f.nativeElement.textContent).toContain('Ley 29733');
    const correo = f.nativeElement.querySelector('a[href^="mailto:"]');
    expect(correo.getAttribute('href')).toBe('mailto:limasoin.sli@gmail.com');
    expect(correo.textContent).toBe('limasoin.sli@gmail.com');
    const enlace = [...f.nativeElement.querySelectorAll('a')].find(
      (elemento: HTMLAnchorElement) => elemento.getAttribute('href') === '/registro',
    );
    expect(enlace.textContent.trim()).toBe('Volver al registro');
    expect(enlace.getAttribute('href')).toBe('/registro');
    expect(enlace.hasAttribute('mat-button')).toBe(true);
  });
});
