import { TestBed } from '@angular/core/testing';
import { SwUpdate } from '@angular/service-worker';
import { Subject } from 'rxjs';
import configuracion from '../../../../ngsw-config.json';
import { Actualizaciones, RECARGAR_PWA } from './actualizaciones';
import { Red } from './red';
import { Teclado } from './teclado';
import { HistorialSuperposiciones } from './historial-superposiciones';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { prepararNegocio } from '../negocio/negocio-prueba';
import { SesionService } from '../auth/sesion-service';
import { ReservaStore } from '../../features/reservar/reserva.store';

describe('Selección pública e identidad', () => {
  it('ir a ingresar conserva la selección pública; limpiar identidad la borra y vacía el store', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ReservaStore],
    });
    const negocio = prepararNegocio();
    const reserva = TestBed.inject(ReservaStore);
    reserva.cliente.set({
      id: 71,
      nombre: 'Cliente ficticio',
      correo: 'cliente-pwa@ejemplo.test',
      telefono: null,
      rol: 'CLIENTE',
      activo: true,
      debeCambiarPassword: false,
    });
    sessionStorage.setItem('barberturno.reserva', 'selección pública');
    negocio.seleccionar(null);
    expect(sessionStorage.getItem('barberturno.reserva')).toBe('selección pública');
    expect(reserva.cliente()).toBeNull();
    TestBed.inject(SesionService).limpiar();
    expect(sessionStorage.getItem('barberturno.reserva')).toBeNull();
    TestBed.inject(HttpTestingController).verify();
  });
});

describe('Caché pública PWA', () => {
  it('solo declara grupos de assets públicos, sin API ni grupos de datos', () => {
    expect(configuracion).not.toHaveProperty('dataGroups');
    expect(configuracion.assetGroups.map((grupo) => grupo.installMode)).toEqual([
      'prefetch',
      'lazy',
    ]);
    for (const grupo of configuracion.assetGroups) {
      expect(grupo.resources).not.toHaveProperty('urls');
      expect(grupo.resources.files.some((patron) => patron.includes('api'))).toBe(false);
      expect(grupo.resources.files).not.toContain('/**');
    }
    for (const ruta of [
      '/api/**',
      '/actuator/**',
      '/.well-known/**',
      '/v3/**',
      '/swagger-ui/**',
      '/**/*.*',
    ]) {
      expect(configuracion.navigationUrls).toContain('!' + ruta);
    }
  });
});

describe('Estado de red', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });
  it('lee navigator al arrancar sin conexión y anuncia la recuperación solo cinco segundos', () => {
    vi.useFakeTimers();
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    const red = TestBed.inject(Red);
    expect(red.sinConexion()).toBe(true);
    online.mockReturnValue(true);
    window.dispatchEvent(new Event('online'));
    expect(red.sinConexion()).toBe(false);
    expect(red.recuperada()).toBe(true);
    vi.advanceTimersByTime(5000);
    expect(red.recuperada()).toBe(false);
    online.mockReturnValue(false);
    window.dispatchEvent(new Event('offline'));
    expect(red.sinConexion()).toBe(true);
  });
});

describe('Actualizaciones controladas', () => {
  const versiones = new Subject<{ type: string }>();
  const irrecuperable = new Subject<object>();
  const activar = vi.fn(async () => true);
  const comprobar = vi.fn(async () => false);
  const recargar = vi.fn();
  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SwUpdate,
          useValue: {
            isEnabled: true,
            versionUpdates: versiones,
            unrecoverable: irrecuperable,
            activateUpdate: activar,
            checkForUpdate: comprobar,
          },
        },
        { provide: RECARGAR_PWA, useValue: recargar },
      ],
    });
  });
  afterEach(() => {
    document.querySelector('#contenido')?.remove();
    document.querySelector('mat-dialog-container')?.remove();
    vi.restoreAllMocks();
  });
  it('VERSION_READY ofrece actualizar sin activar ni recargar hasta pulsar', async () => {
    const servicio = TestBed.inject(Actualizaciones);
    versiones.next({ type: 'VERSION_DETECTED' });
    expect(servicio.disponible()).toBe(false);
    versiones.next({ type: 'VERSION_READY' });
    expect(servicio.disponible()).toBe(true);
    expect(activar).not.toHaveBeenCalled();
    expect(recargar).not.toHaveBeenCalled();
    await servicio.actualizar();
    expect(activar).toHaveBeenCalledTimes(1);
    expect(recargar).toHaveBeenCalledTimes(1);
  });
  it.each(['formulario', 'diálogo'])(
    'con %s pide confirmación y rechazar conserva la versión',
    async (tipo) => {
      const nodo = document.createElement(tipo === 'formulario' ? 'main' : 'mat-dialog-container');
      if (tipo === 'formulario') {
        nodo.id = 'contenido';
        nodo.innerHTML = '<form class="ng-dirty"></form>';
      }
      document.body.append(nodo);
      const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
      const servicio = TestBed.inject(Actualizaciones);
      versiones.next({ type: 'VERSION_READY' });
      await servicio.actualizar();
      expect(confirmar).toHaveBeenCalledTimes(1);
      expect(recargar).not.toHaveBeenCalled();
      expect(activar).not.toHaveBeenCalled();
      confirmar.mockReturnValue(true);
      await servicio.actualizar();
      expect(recargar).toHaveBeenCalledTimes(1);
    },
  );
  it('un fallo de activación conserva la página y permite reintentar', async () => {
    activar.mockRejectedValueOnce(new Error('red'));
    const servicio = TestBed.inject(Actualizaciones);
    versiones.next({ type: 'VERSION_READY' });
    await servicio.actualizar();
    expect(recargar).not.toHaveBeenCalled();
    expect(servicio.error()).toContain('No se pudo actualizar');
    expect(servicio.activando()).toBe(false);
  });
  it('un estado irrecuperable tampoco recarga solo', async () => {
    const servicio = TestBed.inject(Actualizaciones);
    irrecuperable.next({ reason: 'recurso perdido' });
    expect(servicio.irrecuperable()).toBe(true);
    expect(recargar).not.toHaveBeenCalled();
    await servicio.actualizar();
    expect(recargar).toHaveBeenCalledTimes(1);
  });
  it('solo comprueba visible y como máximo cada 30 minutos', () => {
    TestBed.inject(Actualizaciones);
    const tiempo = vi.spyOn(Date, 'now').mockReturnValue(2_000_000);
    const visibilidad = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(comprobar).not.toHaveBeenCalled();
    visibilidad.mockReturnValue('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    document.dispatchEvent(new Event('visibilitychange'));
    expect(comprobar).toHaveBeenCalledTimes(1);
    tiempo.mockReturnValue(3_800_000);
    document.dispatchEvent(new Event('visibilitychange'));
    expect(comprobar).toHaveBeenCalledTimes(2);
  });
});

describe('Historial de superposiciones', () => {
  const marca = 'barberturnoSuperposicion';
  let estado: Record<string, unknown>;
  let pila: Record<string, unknown>[];
  const pop = () => {
    estado = pila.pop()!;
    window.dispatchEvent(new PopStateEvent('popstate', { state: estado }));
  };
  beforeEach(() => {
    estado = { navigationId: 8 };
    pila = [];
    vi.spyOn(history, 'state', 'get').mockImplementation(() => estado);
    vi.spyOn(history, 'pushState').mockImplementation((nuevo) => {
      pila.push(estado);
      estado = nuevo;
    });
    vi.spyOn(history, 'back').mockImplementation(() => {
      queueMicrotask(pop);
    });
  });
  afterEach(() => vi.restoreAllMocks());
  it('Atrás cierra solo la superior, conserva URL y no llega al router', () => {
    const servicio = TestBed.inject(HistorialSuperposiciones);
    const cerrar = vi.fn();
    const url = location.href;
    servicio.abrir(cerrar);
    const router = vi.fn();
    window.addEventListener('popstate', router);
    pop();
    expect(cerrar).toHaveBeenCalledTimes(1);
    expect(router).not.toHaveBeenCalled();
    expect(location.href).toBe(url);
    expect(estado).toEqual({ navigationId: 8 });
    window.removeEventListener('popstate', router);
  });
  it('cierre por botón retira solo su entrada, una sola vez y sin navegar', async () => {
    const servicio = TestBed.inject(HistorialSuperposiciones);
    const cerrar = vi.fn();
    const retirar = servicio.abrir(cerrar);
    retirar();
    retirar();
    await Promise.resolve();
    expect(history.back).toHaveBeenCalledTimes(1);
    expect(cerrar).not.toHaveBeenCalled();
    expect(estado[marca]).toBeUndefined();
  });
  it('diálogos anidados se cierran en orden y después Atrás vuelve al router', () => {
    const servicio = TestBed.inject(HistorialSuperposiciones);
    const orden: number[] = [];
    servicio.abrir(() => orden.push(1));
    servicio.abrir(() => orden.push(2));
    pop();
    expect(orden).toEqual([2]);
    pop();
    expect(orden).toEqual([2, 1]);
    const router = vi.fn();
    window.addEventListener('popstate', router);
    window.dispatchEvent(new PopStateEvent('popstate'));
    expect(router).toHaveBeenCalledTimes(1);
    window.removeEventListener('popstate', router);
    expect(history.back).not.toHaveBeenCalled();
  });
  it('cerrar la inferior antes de la superior depura ambas sin bucles', async () => {
    const servicio = TestBed.inject(HistorialSuperposiciones);
    const inferior = servicio.abrir(vi.fn());
    const superior = servicio.abrir(vi.fn());
    inferior();
    expect(history.back).not.toHaveBeenCalled();
    superior();
    await Promise.resolve();
    await Promise.resolve();
    expect(history.back).toHaveBeenCalledTimes(2);
    expect(estado[marca]).toBeUndefined();
  });
  it('no retrocede si el router ya sustituyó la entrada propia', () => {
    const retirar = TestBed.inject(HistorialSuperposiciones).abrir(vi.fn());
    estado = { navigationId: 9 };
    retirar();
    expect(history.back).not.toHaveBeenCalled();
  });
  it('navegar desde un menú no deja una copia de la ruta al retroceder o avanzar', () => {
    const servicio = TestBed.inject(HistorialSuperposiciones);
    const retirar = servicio.abrir(vi.fn());
    const superposicion = estado;
    const original = location.href;
    const router = vi.fn();
    const mover = vi.spyOn(history, 'go').mockImplementation(() => undefined);
    window.addEventListener('popstate', router);
    try {
      history.replaceState(null, '', original + '?destino-pwa=1');
      servicio.navegacionTerminada();
      estado = { navigationId: 9 };
      retirar();
      expect(history.back).not.toHaveBeenCalled();
      history.replaceState(null, '', original);
      window.dispatchEvent(new PopStateEvent('popstate', { state: superposicion }));
      expect(mover).toHaveBeenLastCalledWith(-1);
      expect(router).not.toHaveBeenCalled();
      servicio.navegacionTerminada();
      window.dispatchEvent(new PopStateEvent('popstate', { state: superposicion }));
      expect(mover).toHaveBeenLastCalledWith(1);
      expect(router).not.toHaveBeenCalled();
    } finally {
      history.replaceState(null, '', original);
      window.removeEventListener('popstate', router);
    }
  });
  it('cerrar todas espera a retirar entradas anidadas antes de permitir navegar', async () => {
    const servicio = TestBed.inject(HistorialSuperposiciones);
    const cerrar = vi.fn();
    servicio.abrir(cerrar);
    servicio.abrir(cerrar);
    let terminado = false;
    const cierre = servicio.cerrarTodas().then(() => {
      terminado = true;
    });
    expect(terminado).toBe(false);
    expect(cerrar).toHaveBeenCalledTimes(2);
    await cierre;
    expect(terminado).toBe(true);
    expect(estado[marca]).toBeUndefined();
    expect(history.back).toHaveBeenCalledTimes(2);
  });
  it('la salida cancela una apertura pendiente sin crear otra entrada', async () => {
    const servicio = TestBed.inject(HistorialSuperposiciones);
    servicio.abrir(vi.fn())();
    const cerrar = vi.fn();
    servicio.abrir(cerrar);
    await servicio.cerrarTodas();
    expect(cerrar).toHaveBeenCalledTimes(1);
    expect(history.pushState).toHaveBeenCalledTimes(1);
    expect(estado[marca]).toBeUndefined();
  });
});

describe('Teclado visual', () => {
  afterEach(() => {
    document.querySelectorAll('input, mat-dialog-container').forEach((nodo) => nodo.remove());
    vi.unstubAllGlobals();
  });
  it('oculta la barra al reducir el viewport con foco y desplaza campos también de diálogos', async () => {
    const viewport = new EventTarget() as VisualViewport;
    Object.assign(viewport, { height: 800, scale: 1 });
    vi.stubGlobal('visualViewport', viewport);
    const teclado = TestBed.inject(Teclado);
    const input = document.createElement('input');
    document.body.append(input);
    input.focus();
    const desplazar = vi.fn();
    input.scrollIntoView = desplazar;
    Object.assign(viewport, { height: 450 });
    viewport.dispatchEvent(new Event('resize'));
    expect(teclado.abierto()).toBe(true);
    await new Promise((resolver) => requestAnimationFrame(resolver));
    expect(desplazar).toHaveBeenCalled();
    const dialogo = document.createElement('mat-dialog-container');
    const otro = document.createElement('input');
    dialogo.append(otro);
    document.body.append(dialogo);
    otro.focus();
    expect(teclado.abierto()).toBe(true);
    Object.assign(viewport, { height: 800 });
    viewport.dispatchEvent(new Event('resize'));
    expect(teclado.abierto()).toBe(false);
  });
  it('zoom sin teclado no oculta la navegación', () => {
    const viewport = new EventTarget();
    Object.assign(viewport, { height: 800, scale: 1 });
    vi.stubGlobal('visualViewport', viewport);
    const teclado = TestBed.inject(Teclado);
    const input = document.createElement('input');
    document.body.append(input);
    input.focus();
    Object.assign(viewport, { height: 400, scale: 2 });
    viewport.dispatchEvent(new Event('resize'));
    expect(teclado.abierto()).toBe(false);
  });
});
