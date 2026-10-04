import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { SesionService } from '../../core/auth/sesion-service';
import { ReservaDto } from '../../core/modelos/reservas';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';
import { ReservaStore } from './reserva.store';

export const servicioPrueba = {
  id: 1,
  nombre: 'Corte',
  descripcion: 'Clásico',
  duracionMin: 30,
  precio: 25,
  activo: true,
};
export const barberosPrueba = [
  { id: 2, nombre: 'Ficticio A', especialidad: 'Corte', activo: true },
  { id: 3, nombre: 'Ficticio B', especialidad: 'Corte', activo: true },
];
export const franjaPrueba = {
  inicio: fechaHoyLima(1) + 'T10:00:00-05:00',
  fin: fechaHoyLima(1) + 'T10:30:00-05:00',
  barberoIds: [2, 3],
};
export const reservaPrueba: ReservaDto = {
  id: 7,
  codigo: 'BT-7',
  cliente: { id: 9, nombre: 'Cliente ficticio' },
  servicio: { id: 1, nombre: 'Corte original' },
  barbero: { id: 2, nombre: 'Ficticio A' },
  inicio: franjaPrueba.inicio,
  fin: franjaPrueba.fin,
  duracionMin: 40,
  precioRef: 22,
  estado: 'CONFIRMADA',
  version: 4,
  permisos: { reprogramar: true, cancelar: true, transiciones: [] },
};

describe('Store de reserva guiada', () => {
  let http: HttpTestingController;
  let store: ReservaStore;
  const router = { navigate: vi.fn().mockResolvedValue(true) };
  beforeEach(() => {
    sessionStorage.clear();
    router.navigate.mockClear();
    TestBed.configureTestingModule({
      providers: [
        ReservaStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(ReservaStore);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });
  function catalogos() {
    http
      .expectOne('/api/servicios?incluirInactivos=false')
      .flush([servicioPrueba, { ...servicioPrueba, id: 10, activo: false }]);
    http.expectOne('/api/barberos?incluirInactivos=false').flush(barberosPrueba);
  }
  function sesion(rol = 'CLIENTE') {
    TestBed.inject(SesionService).cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush({
      id: 9,
      nombre: 'Ficticio',
      correo: 't26@ejemplo.test',
      rol,
      debeCambiarPassword: false,
    });
  }
  function franjas(duracionMin = 30) {
    const r = http.expectOne((r) => r.url === '/api/disponibilidad');
    r.flush({
      servicioId: 1,
      fecha: store.fecha(),
      duracionMin,
      franjas: [{ ...franjaPrueba, fin: fechaHoyLima(1) + 'T10:' + duracionMin + ':00-05:00' }],
    });
    return r.request;
  }
  function seleccion() {
    store.inicializar();
    catalogos();
    store.elegirServicio(1);
    franjas();
    store.elegirFecha(fechaHoyLima(1));
    franjas();
    store.irPaso(1);
    store.elegirFranja(store.franjas()[0]);
  }
  it('bloquea avanzar sin selección y recorre tres pasos con el primer profesional por defecto', () => {
    store.irPaso(2);
    expect(store.paso()).toBe(0);
    seleccion();
    expect(store.paso()).toBe(2);
    expect(store.barberoId()).toBe(2);
    store.elegirBarbero(3);
    expect(store.barberoId()).toBe(3);
    store.elegirBarbero(99);
    expect(store.barberoId()).toBe(3);
  });
  it('sin preferencia omite barberoId y muestra los profesionales de cada franja', () => {
    store.inicializar();
    catalogos();
    store.elegirServicio(1);
    expect(franjas().params.has('barberoId')).toBe(false);
    store.elegirPreferencia(3);
    expect(franjas().params.get('barberoId')).toBe('3');
    store.elegirPreferencia(null);
    expect(franjas().params.has('barberoId')).toBe(false);
    expect(store.franjas()[0].barberoIds).toEqual([2, 3]);
  });
  it('cambiar servicio, fecha o preferencia descarta la franja anterior', () => {
    seleccion();
    store.elegirFecha(fechaHoyLima(2));
    expect(store.franja()).toBeNull();
    expect(store.paso()).toBe(1);
    franjas();
    store.elegirFranja(store.franjas()[0]);
    store.elegirPreferencia(2);
    expect(store.franja()).toBeNull();
    expect(store.paso()).toBe(0);
    franjas();
  });
  it('cancela consultas antiguas para evitar publicar franjas de otra fecha', () => {
    store.inicializar();
    catalogos();
    store.elegirServicio(1);
    const anterior = http.expectOne((r) => r.url === '/api/disponibilidad');
    store.elegirFecha(fechaHoyLima(2));
    expect(anterior.cancelled).toBe(true);
    franjas();
    expect(store.consultando()).toBe(false);
  });
  it('guarda solo la selección, vuelve del login al paso 3 y revalida la franja', () => {
    seleccion();
    store.elegirBarbero(3);
    store.confirmar();
    expect(router.navigate).toHaveBeenCalledWith(['/ingresar'], {
      queryParams: { returnUrl: '/reservar' },
    });
    const guardada = sessionStorage.getItem('barberturno.reserva')!;
    expect(guardada).not.toContain('nombre');
    expect(guardada).not.toContain('correo');
    sesion();
    store.inicializar();
    catalogos();
    expect(store.paso()).toBe(1);
    franjas();
    expect(store.paso()).toBe(2);
    expect(store.barberoId()).toBe(3);
    expect(sessionStorage.getItem('barberturno.reserva')).toBeNull();
  });
  it('si la selección guardada se ocupó, vuelve al paso 2', () => {
    seleccion();
    store.confirmar();
    store.inicializar();
    catalogos();
    http.expectOne((r) => r.url === '/api/disponibilidad').flush({ franjas: [] });
    expect(store.paso()).toBe(1);
    expect(store.franja()).toBeNull();
    expect(store.mensaje()).toContain('ya no está disponible');
  });
  it.each(['{}', '{', '{"servicioId":999}', 'null'])('tolera selección corrupta %s', (texto) => {
    sessionStorage.setItem('barberturno.reserva', texto);
    store.inicializar();
    catalogos();
    expect(store.paso()).toBe(0);
    http.expectNone((r) => r.url === '/api/disponibilidad');
  });
  it('tolera sessionStorage inaccesible y permite ingresar', () => {
    seleccion();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('No permitido');
    });
    store.confirmar();
    expect(router.navigate).toHaveBeenCalled();
    expect(store.mensaje()).toContain('no permite conservar');
  });
  it.each(['FRANJA_NO_DISPONIBLE', 'CLIENTE_CON_RESERVA_SOLAPADA'])(
    '409 %s recarga y vuelve al paso 2',
    (codigo) => {
      seleccion();
      sesion();
      store.confirmar();
      const r = http.expectOne('/api/reservas');
      expect(r.request.body).toEqual({ servicioId: 1, barberoId: 2, inicio: franjaPrueba.inicio });
      r.flush(
        { codigo, detail: 'Conflicto del servidor' },
        { status: 409, statusText: 'Conflict' },
      );
      expect(store.paso()).toBe(1);
      expect(store.franja()).toBeNull();
      expect(store.consultando()).toBe(true);
      franjas();
      expect(store.enviando()).toBe(false);
      expect(store.mensaje()).not.toBe('');
    },
  );
  it('el límite activo presenta el detalle y habilita el enlace a Mis citas', () => {
    seleccion();
    sesion();
    store.confirmar();
    http
      .expectOne('/api/reservas')
      .flush(
        { codigo: 'LIMITE_RESERVAS_ACTIVAS', detail: 'Tiene tres reservas activas.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    expect(store.limiteReservas()).toBe(true);
    expect(store.mensaje()).toBe('Tiene tres reservas activas.');
  });
  it('otros códigos conservan ProblemDetail y permiten corregir sin reconsultar', () => {
    seleccion();
    sesion();
    store.confirmar();
    http
      .expectOne('/api/reservas')
      .flush(
        { codigo: 'INICIO_EN_PASADO', detail: 'El inicio ya pasó.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    expect(store.mensaje()).toBe('El inicio ya pasó.');
    expect(store.paso()).toBe(2);
  });
  it('solo envía una petición y tras éxito navega a Mis citas', () => {
    seleccion();
    sesion();
    store.confirmar();
    store.confirmar();
    expect(store.enviando()).toBe(true);
    expect(store.puedeConfirmar()).toBe(false);
    http.expectOne('/api/reservas').flush(reservaPrueba);
    expect(store.enviando()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/mis-citas'], {
      state: { avisoReserva: 'Reserva creada.' },
    });
  });
  it('BARBERO explora pero no envía reservas', () => {
    seleccion();
    sesion('BARBERO');
    store.confirmar();
    expect(store.puedeConfirmar()).toBe(false);
    http.expectNone('/api/reservas');
  });
  function reprogramar(permitida = true) {
    store.inicializar(7);
    catalogos();
    http
      .expectOne('/api/reservas/7')
      .flush({ ...reservaPrueba, permisos: { ...reservaPrueba.permisos, reprogramar: permitida } });
    store.elegirServicio(10);
    expect(store.servicioId()).toBe(1);
    store.cargarFranjas();
    const solicitud = franjas(40);
    expect(solicitud.params.get('excluirReservaId')).toBe('7');
    store.elegirFranja(store.franjas()[0]);
  }
  it('reprogramación conserva referencias y envía endpoint, barbero y versión correctos', () => {
    sesion();
    reprogramar();
    expect(store.servicio()?.precio).toBe(22);
    expect(store.servicio()?.duracionMin).toBe(40);
    store.confirmar();
    const r = http.expectOne('/api/reservas/7/reprogramacion');
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({ inicio: franjaPrueba.inicio, barberoId: 2, version: 4 });
    r.flush(reservaPrueba);
  });
  it.each([true, false])(
    'usa la respuesta de reprogramación con catálogo activo=%s y duración distinta',
    (activo) => {
      sesion();
      store.inicializar(7);
      http
        .expectOne('/api/servicios?incluirInactivos=false')
        .flush([{ ...servicioPrueba, duracionMin: 60, precio: 99, activo }]);
      http.expectOne('/api/barberos?incluirInactivos=false').flush(barberosPrueba);
      http.expectOne('/api/reservas/7').flush(reservaPrueba);
      store.cargarFranjas();
      const consulta = http.expectOne((r) => r.url === '/api/disponibilidad');
      expect(consulta.request.params.get('servicioId')).toBe('1');
      expect(consulta.request.params.get('excluirReservaId')).toBe('7');
      const franja = { ...franjaPrueba, fin: fechaHoyLima(1) + 'T10:40:00-05:00' };
      consulta.flush({ servicioId: 1, fecha: store.fecha(), duracionMin: 40, franjas: [franja] });
      expect(store.duracionDisponibilidad()).toBe(40);
      expect(store.servicio()?.duracionMin).toBe(40);
      expect(store.servicio()?.precio).toBe(22);
      expect(store.franjas()).toEqual([franja]);
      store.elegirFranja(store.franjas()[0]);
      expect(store.franja()?.fin).toBe(franja.fin);
      store.confirmar();
      const envio = http.expectOne('/api/reservas/7/reprogramacion');
      expect(envio.request.body).toEqual({ inicio: franja.inicio, barberoId: 2, version: 4 });
      envio.flush(reservaPrueba);
    },
  );
  it('ADMIN requiere motivo de experiencia de usuario y lo envía en reprogramación', () => {
    sesion('ADMIN');
    reprogramar();
    store.confirmar();
    http.expectNone('/api/reservas/7/reprogramacion');
    store.motivo.set('  Ajuste solicitado por cliente  ');
    store.confirmar();
    const r = http.expectOne('/api/reservas/7/reprogramacion');
    expect(r.request.body.motivo).toBe('Ajuste solicitado por cliente');
    r.flush(reservaPrueba);
  });
  it('respeta permisos del servidor sin calcular la regla temporal en frontend', () => {
    sesion();
    reprogramar(false);
    store.confirmar();
    expect(store.puedeConfirmar()).toBe(false);
    http.expectNone('/api/reservas/7/reprogramacion');
  });
  it('404 de detalle se muestra sin simular reserva', () => {
    store.inicializar(7);
    catalogos();
    http
      .expectOne('/api/reservas/7')
      .flush({ detail: 'Reserva no encontrada.' }, { status: 404, statusText: 'Not Found' });
    expect(store.reserva()).toBeNull();
    expect(store.mensaje()).toBe('Reserva no encontrada.');
  });
});
