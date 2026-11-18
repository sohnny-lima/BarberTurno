import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { erroresInterceptor } from '../../core/auth/errores-interceptor';
import { prepararNegocio } from '../../core/negocio/negocio-prueba';
import { WhatsAppProfesional } from './whatsapp-profesional';

describe('WhatsAppProfesional', () => {
  let http: HttpTestingController;
  const abrir = vi.fn();
  const operaciones = [
    ['cargar', 'GET'],
    ['generar', 'POST'],
    ['desvincular', 'DELETE'],
  ] as const;

  beforeEach(() => {
    abrir.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([erroresInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: abrir } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    prepararNegocio();
  });

  afterEach(() => http.verify());

  function crear(vinculado = false, vinculadoEn: string | null = null) {
    const fixture = TestBed.createComponent(WhatsAppProfesional);
    const req = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
    expect(req.request.method).toBe('GET');
    req.flush({ vinculado, vinculadoEn });
    fixture.detectChanges();
    return fixture;
  }

  it('muestra el aviso y el estado no vinculado inicialmente', () => {
    const f = crear(false);
    const texto = f.nativeElement.textContent;

    expect(texto).toContain('WhatsApp de la barbería');
    expect(texto).toContain('Todavía no has vinculado tu WhatsApp.');
    expect(texto).toContain('Se guardará el identificador de tu WhatsApp para enviarte tu agenda.');

    // Botón generar visible, desvincular ausente
    expect(
      f.nativeElement.querySelector('button[aria-describedby="aviso-whatsapp-profesional"]'),
    ).not.toBeNull();
    const botones = Array.from(f.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLElement).textContent?.trim(),
    );
    expect(botones).toContain('Generar código');
    expect(botones).not.toContain('Desvincular');
  });

  it('muestra el estado vinculado y el botón de desvincular', () => {
    const f = crear(true, '2026-11-12T10:00:00Z');
    const texto = f.nativeElement.textContent;

    expect(texto).toContain('Tu WhatsApp está vinculado a tu agenda.');
    expect(texto).toContain('Vinculado el');

    const botones = Array.from(f.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLElement).textContent?.trim(),
    );
    expect(botones).toContain('Desvincular');
  });

  it('genera un código y muestra las instrucciones con la caducidad', () => {
    const f = crear(false);
    const botonGenerar = Array.from(f.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLElement).textContent?.includes('Generar código'),
    ) as HTMLElement;

    botonGenerar.click();

    const postReq = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
    expect(postReq.request.method).toBe('POST');
    postReq.flush({ codigo: '9XYZ-4W2P', caducaEn: '2026-11-12T10:10:00Z' });
    f.detectChanges();

    const texto = f.nativeElement.textContent;
    expect(texto).toContain('9XYZ-4W2P');
    expect(texto).toContain('Envía «vincular 9XYZ-4W2P» al WhatsApp de la barbería.');
    expect(texto).toContain('Es de un solo uso.');
    expect(texto).toContain('Caduca el');
    expect(texto).toContain('«agenda hoy» o «agenda mañana»');
  });

  it('permite desvincular y actualiza el estado a no vinculado', () => {
    const f = crear(true, '2026-11-12T10:00:00Z');
    const botonDesvincular = Array.from(f.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLElement).textContent?.includes('Desvincular'),
    ) as HTMLElement;

    botonDesvincular.click();

    const delReq = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null, { status: 204, statusText: 'No Content' });
    f.detectChanges();

    const texto = f.nativeElement.textContent;
    expect(texto).toContain('Todavía no has vinculado tu WhatsApp.');
    expect(f.componentInstance.estado()?.vinculado).toBe(false);

    const botones = Array.from(f.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLElement).textContent?.trim(),
    );
    expect(botones).not.toContain('Desvincular');
  });

  it.each(operaciones)(
    '%s: un 404 oculta el panel sin mostrar alerta ni snackbar',
    async (accion, metodo) => {
      const fixture =
        accion === 'cargar' ? TestBed.createComponent(WhatsAppProfesional) : crear(true);
      if (accion !== 'cargar') fixture.componentInstance[accion]();
      const req = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
      expect(req.request.method).toBe(metodo);
      req.flush(
        { detail: 'No se encontró el perfil profesional.' },
        { status: 404, statusText: 'Not Found' },
      );
      fixture.detectChanges();
      // El interceptor decide el snackbar después del manejador de la vista.
      await new Promise<void>((resolver) => queueMicrotask(resolver));

      expect(fixture.componentInstance.disponible()).toBe(false);
      expect(fixture.componentInstance.ocupado()).toBe(false);
      expect(fixture.componentInstance.error()).toBe('');
      expect(fixture.nativeElement.querySelector('section')).toBeNull();
      expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
      expect(abrir).not.toHaveBeenCalled();
    },
  );

  it.each(operaciones)(
    '%s: un 500 conserva el error junto a la acción sin duplicarlo en snackbar',
    async (accion, metodo) => {
      const fixture =
        accion === 'cargar' ? TestBed.createComponent(WhatsAppProfesional) : crear(true);
      if (accion !== 'cargar') fixture.componentInstance[accion]();
      const req = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
      expect(req.request.method).toBe(metodo);
      req.flush(
        { detail: 'Detalle técnico que no debe mostrarse.' },
        { status: 500, statusText: 'Internal Server Error' },
      );
      fixture.detectChanges();
      await new Promise<void>((resolver) => queueMicrotask(resolver));

      expect(fixture.componentInstance.disponible()).toBe(true);
      expect(fixture.componentInstance.ocupado()).toBe(false);
      expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toBe(
        'No pudimos actualizar el vínculo. Vuelve a intentarlo.',
      );
      expect(fixture.nativeElement.textContent).not.toContain('Detalle técnico');
      expect(abrir).not.toHaveBeenCalled();
    },
  );
});
