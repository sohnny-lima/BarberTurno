import { TestBed } from '@angular/core/testing';
import { EnlaceInvitacion } from './enlace-invitacion';

describe('Enlace de invitación efímero', () => {
  it('no inventa una caducidad cuando la respuesta no la incluye', () => {
    const f = TestBed.createComponent(EnlaceInvitacion);
    f.componentRef.setInput('enlace', '/api/invitaciones/capacidad-ficticia');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('El enlace se muestra una sola vez.');
    expect(f.nativeElement.textContent).not.toContain('caduca');
    expect(f.nativeElement.textContent).not.toContain('siete');
  });
  it('copia la URL completa y anuncia el resultado sin persistir el token', async () => {
    const copiar = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: copiar },
    });
    const persistir = vi.spyOn(Storage.prototype, 'setItem');
    const f = TestBed.createComponent(EnlaceInvitacion);
    f.componentRef.setInput('enlace', '/api/invitaciones/capacidad-ficticia');
    f.componentRef.setInput('expiraEn', '2026-10-02T03:00:00Z');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('01/10/2026, 22:00');
    await f.componentInstance.copiar();
    expect(copiar).toHaveBeenCalledWith(location.origin + '/invitacion/capacidad-ficticia');
    expect(f.componentInstance.mensaje()).toBe('Enlace copiado.');
    expect(persistir).not.toHaveBeenCalled();
    persistir.mockRestore();
  });
  it('permite copiar manualmente cuando el portapapeles falla', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockRejectedValue(new Error()) },
    });
    const f = TestBed.createComponent(EnlaceInvitacion);
    f.componentRef.setInput('enlace', '/invitacion/capacidad-ficticia');
    await f.componentInstance.copiar();
    expect(f.componentInstance.mensaje()).toContain('Seleccione el enlace');
  });
});
