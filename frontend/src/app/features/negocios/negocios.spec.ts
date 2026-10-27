import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { OverlayContainer } from '@angular/cdk/overlay';
import { Router, provideRouter } from '@angular/router';
import { NegocioPropioDto } from '../../core/modelos/negocios';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { Negocios } from './negocios';
import { PapelDialogo } from './papel-dialogo';
import { BarberiaActual } from '../../layout/barberia-actual';
import { prepararNegocio } from '../../core/negocio/negocio-prueba';

describe('Selector y elección de papel', () => {
  let http: HttpTestingController;
  const norte: NegocioPropioDto = {
    slug: 'norte',
    nombre: 'Barbería Norte',
    rol: 'PROPIETARIO',
    papeles: ['PROPIETARIO', 'CLIENTE'],
  };
  const sur: NegocioPropioDto = {
    slug: 'sur',
    nombre: 'Barbería Sur',
    rol: 'CLIENTE',
    papeles: ['CLIENTE'],
  };
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });
  function crear(filas: NegocioPropioDto[]) {
    const vista = TestBed.createComponent(Negocios);
    http.expectOne('/api/mis-negocios').flush(filas);
    vista.detectChanges();
    return vista;
  }
  it('varias barberías: última usada primero, papeles visibles y ninguna entrada automática', () => {
    localStorage.setItem('barberturno.ultima-barberia', 'sur');
    const vista = crear([norte, sur]);
    const filas = vista.nativeElement.querySelectorAll('ul[aria-label="Barberías"] li');
    expect(filas.length).toBe(2);
    expect(filas[0].textContent).toContain('Barbería Sur');
    expect(filas[0].textContent).toContain('Última usada');
    expect(filas[1].textContent).toContain('Propietario y Cliente');
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });
  it('una sola barbería de cliente entra directamente y recuerda solo el slug', () => {
    crear([sur]);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/n', 'sur', 'reservar'], {
      queryParams: { papel: 'cliente' },
    });
    expect(localStorage.getItem('barberturno.ultima-barberia')).toBe('sur');
    expect(TestBed.inject(MatDialog).openDialogs.length).toBe(0);
  });
  it('una sola relación de personal entra a agenda sin diálogo', () => {
    crear([{ ...norte, papeles: ['PROPIETARIO'] }]);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/n', 'norte', 'agenda'], {
      queryParams: {},
    });
    expect(TestBed.inject(MatDialog).openDialogs.length).toBe(0);
  });
  it('ninguna relación muestra orientación de invitaciones, sin inventar una barbería', () => {
    const vista = crear([]);
    expect(vista.nativeElement.textContent).toContain(
      'Todavía no pertenece a ninguna barbería. Si recibió una invitación, ábrala desde su enlace.',
    );
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });
  it.each(['CLIENTE', 'PERSONAL'] as const)(
    'dos papeles exigen diálogo y respetan la elección %s',
    async (papel) => {
      const vista = crear([norte]);
      const dialogo = TestBed.inject(MatDialog).openDialogs[0];
      expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
      expect(dialogo.componentInstance).toBeInstanceOf(PapelDialogo);
      vista.detectChanges();
      const contenido = TestBed.inject(OverlayContainer).getContainerElement();
      expect(contenido.textContent).toContain('¿Cómo quiere entrar en Barbería Norte?');
      expect(contenido.textContent).toContain('Como personal (Propietario)');
      expect(contenido.querySelectorAll('mat-radio-button').length).toBe(2);
      (dialogo.componentInstance as PapelDialogo).papel.set(papel);
      dialogo.close(papel);
      await vista.whenStable();
      await vi.waitFor(() => expect(TestBed.inject(Router).navigate).toHaveBeenCalled());
      expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(
        ['/n', 'norte', papel === 'CLIENTE' ? 'reservar' : 'agenda'],
        { queryParams: papel === 'CLIENTE' ? { papel: 'cliente' } : {} },
      );
    },
  );
  it('cancelar el diálogo conserva el selector y no navega', async () => {
    const vista = crear([norte]);
    TestBed.inject(MatDialog).openDialogs[0].close();
    await vista.whenStable();
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
    expect(vista.nativeElement.textContent).toContain('Barbería Norte');
  });
  it('localStorage denegado no impide listar ni entrar', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('Denegado');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('Denegado');
    });
    crear([sur]);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/n', 'sur', 'reservar'], {
      queryParams: { papel: 'cliente' },
    });
  });
  it('el error de listado permite reintentar conservando el aviso de revocación', () => {
    TestBed.inject(NegocioActual).avisoSelector.set('Ya no tiene acceso a Barbería Norte.');
    const vista = TestBed.createComponent(Negocios);
    http.expectOne('/api/mis-negocios').flush({}, { status: 503, statusText: 'Unavailable' });
    vista.detectChanges();
    expect(vista.nativeElement.textContent).toContain('No pudimos consultar sus barberías.');
    vista.componentInstance.cargar();
    http.expectOne('/api/mis-negocios').flush([sur]);
    vista.detectChanges();
    expect(vista.nativeElement.textContent).toContain('Ya no tiene acceso a Barbería Norte.');
    expect(vista.nativeElement.textContent).toContain('Barbería Sur');
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });
  it('el bloque muestra barbería y papel efectivo; Cambiar papel abre las mismas opciones', async () => {
    const negocio = prepararNegocio('ADMIN');
    negocio.actualizarSesion({ ...negocio.usuario()!, rolNegocio: 'PROPIETARIO' });
    const vista = TestBed.createComponent(BarberiaActual);
    vista.detectChanges();
    http.expectOne('/api/mis-negocios').flush([{ ...norte, slug: 'prueba' }]);
    vista.detectChanges();
    expect(vista.nativeElement.textContent).toContain('Barbería ficticia');
    expect(vista.nativeElement.textContent).toContain('Propietario');
    expect(vista.nativeElement.querySelector('a').getAttribute('href')).toBe('/negocios');
    vista.componentInstance.cambiarPapel();
    expect(TestBed.inject(MatDialog).openDialogs.length).toBe(1);
    TestBed.inject(MatDialog).openDialogs[0].close('CLIENTE');
    await vista.whenStable();
    await vi.waitFor(() => expect(TestBed.inject(Router).navigate).toHaveBeenCalled());
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/n', 'prueba', 'reservar'], {
      queryParams: { papel: 'cliente' },
    });
    negocio.actualizarSesion({ ...negocio.usuario()!, rol: 'CLIENTE', rolNegocio: null });
    vista.detectChanges();
    http.expectOne('/api/mis-negocios').flush([{ ...norte, slug: 'prueba' }]);
    vista.detectChanges();
    expect(vista.nativeElement.textContent).toContain('Cliente');
  });
});
