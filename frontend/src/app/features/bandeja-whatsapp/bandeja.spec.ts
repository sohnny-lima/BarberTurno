import { BreakpointObserver } from '@angular/cdk/layout';
import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localeEsPe from '@angular/common/locales/es-PE';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import {
  ConversacionBandejaDto,
  DetalleConversacionDto,
  MensajeBandejaDto,
} from '../../core/modelos/bandeja-whatsapp';
import { prepararNegocio } from '../../core/negocio/negocio-prueba';
import { Bandeja, ConfirmarDevolucionDialogo } from './bandeja';

registerLocaleData(localeEsPe);

describe('Bandeja de WhatsApp (V2-11)', () => {
  let http: HttpTestingController;
  let paramsSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let movilSubject: BehaviorSubject<{ matches: boolean }>;
  let dialogResult: Subject<boolean | undefined>;
  let fixtureActual: ComponentFixture<Bandeja> | null = null;

  const CONVERSACION_1: ConversacionBandejaDto = {
    id: 10,
    cliente: 'Juan Pérez',
    telefonoEnmascarado: '••••••123',
    motivo: 'PETICION',
    derivadaEn: '2026-11-04T10:00:00-05:00',
    ultimaEntradaEn: '2026-11-04T10:15:00-05:00',
    ventanaAbiertaHasta: '2026-11-05T10:15:00-05:00',
    pendienteDeRespuesta: true,
    puedeResponder: true,
    motivoNoResponder: null,
  };

  const CONVERSACION_BLOQUEADA: ConversacionBandejaDto = {
    id: 20,
    cliente: 'María García',
    telefonoEnmascarado: '••••••456',
    motivo: 'SIN_PROGRESO',
    derivadaEn: '2026-11-03T10:00:00-05:00',
    ultimaEntradaEn: '2026-11-03T10:15:00-05:00',
    ventanaAbiertaHasta: '2026-11-04T10:15:00-05:00',
    pendienteDeRespuesta: false,
    puedeResponder: false,
    motivoNoResponder: 'FUERA_DE_VENTANA',
  };

  const MENSAJE_CLIENTE: MensajeBandejaDto = {
    id: 1,
    entrante: true,
    origen: 'CLIENTE',
    texto: 'Hola, necesito hablar con una persona',
    retirado: false,
    estado: 'RECIBIDO',
    motivoFallo: null,
    creadoEn: '2026-11-04T10:15:00-05:00',
    enviadoPor: null,
  };

  const MENSAJE_PERSONAL: MensajeBandejaDto = {
    id: 2,
    entrante: false,
    origen: 'PERSONA',
    texto: 'Hola Juan, ¿en qué te podemos ayudar?',
    retirado: false,
    estado: 'ENVIADO',
    motivoFallo: null,
    creadoEn: '2026-11-04T10:20:00-05:00',
    enviadoPor: 'Carlos Barbero',
  };

  const MENSAJE_RETIRADO: MensajeBandejaDto = {
    id: 3,
    entrante: true,
    origen: 'CLIENTE',
    texto: null,
    retirado: true,
    estado: 'RECIBIDO',
    motivoFallo: null,
    creadoEn: '2026-11-04T09:00:00-05:00',
    enviadoPor: null,
  };

  const MENSAJE_FALLIDO: MensajeBandejaDto = {
    id: 4,
    entrante: false,
    origen: 'PERSONA',
    texto: 'Mensaje fallido',
    retirado: false,
    estado: 'FALLIDO',
    motivoFallo: 'VENTANA_CERRADA',
    creadoEn: '2026-11-04T10:30:00-05:00',
    enviadoPor: 'Carlos Barbero',
  };

  const DETALLE_1: DetalleConversacionDto = {
    conversacion: CONVERSACION_1,
    mensajes: [MENSAJE_CLIENTE, MENSAJE_PERSONAL],
    hayAnteriores: true,
  };

  beforeEach(() => {
    paramsSubject = new BehaviorSubject(convertToParamMap({}));
    movilSubject = new BehaviorSubject<{ matches: boolean }>({ matches: false });
    dialogResult = new Subject();

    TestBed.configureTestingModule({
      imports: [Bandeja],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramsSubject.asObservable(),
          },
        },
        {
          provide: BreakpointObserver,
          useValue: {
            observe: () => movilSubject,
          },
        },
        {
          provide: MatDialog,
          useValue: {
            open: vi.fn(() => ({
              afterClosed: () => dialogResult,
            })),
          },
        },
      ],
    });

    http = TestBed.inject(HttpTestingController);
    prepararNegocio();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      fixtureActual?.destroy();
      fixtureActual = null;
      TestBed.resetTestingModule();
    }
  });

  function inicializar(conversaciones = [CONVERSACION_1]): ComponentFixture<Bandeja> {
    const fixture = TestBed.createComponent(Bandeja);
    fixtureActual = fixture;
    fixture.detectChanges();
    const req = http.expectOne(
      (r) => r.url === '/api/n/prueba/whatsapp/conversaciones' && r.params.get('pagina') === '0',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      contenido: conversaciones,
      totalElementos: conversaciones.length,
      pagina: 0,
      tamano: 50,
      totalPaginas: conversaciones.length > 0 ? 1 : 0,
    });
    fixture.detectChanges();
    return fixture;
  }

  it('lista conversaciones derivadas con cliente, teléfono, motivo legible y hora en Lima', () => {
    const fixture = inicializar([CONVERSACION_1, CONVERSACION_BLOQUEADA]);
    const items = fixture.nativeElement.querySelectorAll('.item-fila');
    expect(items.length).toBe(2);

    expect(items[0].textContent).toContain('Juan Pérez');
    expect(items[0].textContent).toContain('••••••123');
    expect(items[0].textContent).toContain('Petición del cliente');
    expect(items[0].textContent).toContain('Sin responder');
    expect(items[0].textContent).toContain('10:15');

    expect(items[1].textContent).toContain('María García');
    expect(items[1].textContent).toContain('Sin progreso');
    expect(items[1].querySelector('.pildora-pendiente')).toBeNull();
  });

  it('muestra estado vacío cuando no hay conversaciones derivadas', () => {
    const fixture = inicializar([]);
    expect(fixture.nativeElement.textContent).toContain('No hay conversaciones derivadas');
  });

  it('al seleccionar una conversación carga el detalle y muestra el historial de mensajes', () => {
    paramsSubject.next(convertToParamMap({ id: '10' }));
    const fixture = inicializar([CONVERSACION_1]);

    const reqDetalle = http.expectOne('/api/n/prueba/whatsapp/conversaciones/10');
    expect(reqDetalle.request.method).toBe('GET');
    reqDetalle.flush(DETALLE_1);
    fixture.detectChanges();

    const detalleCabecera = fixture.nativeElement.querySelector('.detalle-cabecera');
    expect(detalleCabecera.textContent).toContain('Juan Pérez');
    expect(detalleCabecera.textContent).toContain('••••••123');

    const burbujas = fixture.nativeElement.querySelectorAll('.burbuja-mensaje');
    expect(burbujas.length).toBe(2);

    expect(burbujas[0].textContent).toContain('Cliente');
    expect(burbujas[0].textContent).toContain('Hola, necesito hablar con una persona');

    expect(burbujas[1].textContent).toContain('Carlos Barbero');
    expect(burbujas[1].textContent).toContain('Hola Juan, ¿en qué te podemos ayudar?');

    // Botón Ver anteriores visible
    const btnAnteriores = fixture.nativeElement.querySelector('.btn-anteriores');
    expect(btnAnteriores).not.toBeNull();
  });

  it('muestra mensaje retirado y mensaje fallido con motivo de error', () => {
    paramsSubject.next(convertToParamMap({ id: '10' }));
    const fixture = inicializar([CONVERSACION_1]);

    http.expectOne('/api/n/prueba/whatsapp/conversaciones/10').flush({
      conversacion: CONVERSACION_1,
      mensajes: [MENSAJE_RETIRADO, MENSAJE_FALLIDO],
      hayAnteriores: false,
    });
    fixture.detectChanges();

    const burbujas = fixture.nativeElement.querySelectorAll('.burbuja-mensaje');
    expect(burbujas[0].textContent).toContain('Mensaje retirado por la política de retención');
    expect(burbujas[1].textContent).toContain('No se pudo enviar: VENTANA_CERRADA');
  });

  it('ver anteriores solicita mensajes anteriores y los antepone cronológicamente', () => {
    paramsSubject.next(convertToParamMap({ id: '10' }));
    const fixture = inicializar([CONVERSACION_1]);

    http.expectOne('/api/n/prueba/whatsapp/conversaciones/10').flush(DETALLE_1);
    fixture.detectChanges();

    const btnAnteriores = fixture.nativeElement.querySelector(
      '.btn-anteriores',
    ) as HTMLButtonElement;
    btnAnteriores.click();
    fixture.detectChanges();

    const reqAnteriores = http.expectOne(
      (r) =>
        r.url === '/api/n/prueba/whatsapp/conversaciones/10' && r.params.get('antesDe') === '1',
    );
    expect(reqAnteriores.request.method).toBe('GET');
    reqAnteriores.flush({
      conversacion: CONVERSACION_1,
      mensajes: [MENSAJE_RETIRADO],
      hayAnteriores: false,
    });
    fixture.detectChanges();

    const burbujas = fixture.nativeElement.querySelectorAll('.burbuja-mensaje');
    expect(burbujas.length).toBe(3);
    expect(burbujas[0].textContent).toContain('Mensaje retirado');
    expect(fixture.nativeElement.querySelector('.btn-anteriores')).toBeNull();
  });

  it('responder habilitado envía texto, agrega mensaje y limpia el campo', () => {
    paramsSubject.next(convertToParamMap({ id: '10' }));
    const fixture = inicializar([CONVERSACION_1]);

    http.expectOne('/api/n/prueba/whatsapp/conversaciones/10').flush(DETALLE_1);
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('#texto-respuesta') as HTMLTextAreaElement;
    expect(textarea.disabled).toBe(false);

    const btnEnviar = fixture.nativeElement.querySelector('.btn-enviar') as HTMLButtonElement;
    expect(btnEnviar.disabled).toBe(true);

    fixture.componentInstance.textoRespuesta.set('Nueva respuesta de prueba');
    fixture.detectChanges();
    expect(btnEnviar.disabled).toBe(false);

    btnEnviar.click();
    fixture.detectChanges();

    const reqPost = http.expectOne('/api/n/prueba/whatsapp/conversaciones/10/respuestas');
    expect(reqPost.request.method).toBe('POST');
    expect(reqPost.request.body).toEqual({ texto: 'Nueva respuesta de prueba' });

    const nuevoMensaje: MensajeBandejaDto = {
      id: 5,
      entrante: false,
      origen: 'PERSONA',
      texto: 'Nueva respuesta de prueba',
      retirado: false,
      estado: 'PENDIENTE',
      motivoFallo: null,
      creadoEn: '2026-11-04T10:45:00-05:00',
      enviadoPor: 'Carlos Barbero',
    };
    reqPost.flush(nuevoMensaje);

    // Tras responder recarga la lista para actualizar marcas
    const reqLista = http.expectOne(
      (r) => r.url === '/api/n/prueba/whatsapp/conversaciones' && r.params.get('pagina') === '0',
    );
    expect(reqLista.request.method).toBe('GET');
    reqLista.flush({
      contenido: [CONVERSACION_1],
      totalElementos: 1,
      pagina: 0,
      tamano: 50,
      totalPaginas: 1,
    });

    fixture.detectChanges();
    expect(fixture.componentInstance.textoRespuesta()).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Nueva respuesta de prueba');
    expect(fixture.nativeElement.textContent).toContain('Pendiente');
  });

  it('responder deshabilitado cuando puedeResponder es false, mostrando el motivo', () => {
    paramsSubject.next(convertToParamMap({ id: '20' }));
    const fixture = inicializar([CONVERSACION_BLOQUEADA]);

    http.expectOne('/api/n/prueba/whatsapp/conversaciones/20').flush({
      conversacion: CONVERSACION_BLOQUEADA,
      mensajes: [MENSAJE_CLIENTE],
      hayAnteriores: false,
    });
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('#texto-respuesta') as HTMLTextAreaElement;
    expect(textarea.disabled).toBe(true);

    const btnEnviar = fixture.nativeElement.querySelector('.btn-enviar') as HTMLButtonElement;
    expect(btnEnviar.disabled).toBe(true);

    expect(fixture.nativeElement.textContent).toContain(
      'La ventana de 24 h de WhatsApp está cerrada: solo puede responder cuando el cliente vuelva a escribir',
    );
  });

  it('devolver al asistente solicita confirmación y vuelve a la lista al aceptar', async () => {
    paramsSubject.next(convertToParamMap({ id: '10' }));
    const fixture = inicializar([CONVERSACION_1]);

    http.expectOne('/api/n/prueba/whatsapp/conversaciones/10').flush(DETALLE_1);
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const btnDevolver = fixture.nativeElement.querySelector('.btn-devolver') as HTMLButtonElement;
    btnDevolver.click();

    const dialog = TestBed.inject(MatDialog);
    expect(dialog.open).toHaveBeenCalledWith(ConfirmarDevolucionDialogo, {
      data: { cliente: 'Juan Pérez' },
    });

    // Confirmar diálogo
    dialogResult.next(true);

    const reqDevolver = http.expectOne('/api/n/prueba/whatsapp/conversaciones/10/devolver');
    expect(reqDevolver.request.method).toBe('POST');
    reqDevolver.flush(null, { status: 204, statusText: 'No Content' });

    expect(navigateSpy).toHaveBeenCalledWith(['/n/prueba/whatsapp']);

    // Recarga la lista
    const reqLista = http.expectOne(
      (r) => r.url === '/api/n/prueba/whatsapp/conversaciones' && r.params.get('pagina') === '0',
    );
    expect(reqLista.request.method).toBe('GET');
    reqLista.flush({ contenido: [], totalElementos: 0, pagina: 0, tamano: 50, totalPaginas: 0 });
    fixture.detectChanges();
  });

  it('maneja errores 404 y 422 adecuadamente', () => {
    paramsSubject.next(convertToParamMap({ id: '99' }));
    const fixture = inicializar([CONVERSACION_1]);

    http.expectOne('/api/n/prueba/whatsapp/conversaciones/99').flush(null, {
      status: 404,
      statusText: 'Not Found',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Esta conversación ya no está derivada');

    // Manejo de error 422 al intentar responder fuera de ventana
    paramsSubject.next(convertToParamMap({ id: '10' }));
    http.expectOne('/api/n/prueba/whatsapp/conversaciones/10').flush(DETALLE_1);
    fixture.detectChanges();

    fixture.componentInstance.textoRespuesta.set('Intentar responder');
    fixture.detectChanges();
    fixture.componentInstance.enviarRespuesta();
    fixture.detectChanges();

    const reqPost = http.expectOne('/api/n/prueba/whatsapp/conversaciones/10/respuestas');
    expect(reqPost.request.method).toBe('POST');
    reqPost.flush(null, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'La ventana de 24 h de WhatsApp está cerrada: solo puede responder cuando el cliente vuelva a escribir',
    );
  });

  it('botón actualizar refresca la lista y el detalle seleccionado', () => {
    paramsSubject.next(convertToParamMap({ id: '10' }));
    const fixture = inicializar([CONVERSACION_1]);

    http.expectOne('/api/n/prueba/whatsapp/conversaciones/10').flush(DETALLE_1);
    fixture.detectChanges();

    const btnActualizar = fixture.nativeElement.querySelector(
      'button[aria-label="Actualizar conversaciones"]',
    ) as HTMLButtonElement;
    btnActualizar.click();

    const reqLista = http.expectOne(
      (r) => r.url === '/api/n/prueba/whatsapp/conversaciones' && r.params.get('pagina') === '0',
    );
    expect(reqLista.request.method).toBe('GET');
    reqLista.flush({
      contenido: [CONVERSACION_1],
      totalElementos: 1,
      pagina: 0,
      tamano: 50,
      totalPaginas: 1,
    });

    const reqDetalle = http.expectOne('/api/n/prueba/whatsapp/conversaciones/10');
    expect(reqDetalle.request.method).toBe('GET');
    reqDetalle.flush(DETALLE_1);
    fixture.detectChanges();
  });
});
