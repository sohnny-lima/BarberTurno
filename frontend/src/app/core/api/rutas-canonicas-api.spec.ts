import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApi } from './auth-api';
import { BarberosApi } from './barberos-api';
import { ConfiguracionApi } from './configuracion-api';
import { DisponibilidadApi } from './disponibilidad-api';
import { EquipoApi } from './equipo-api';
import { HorariosApi } from './horarios-api';
import { InvitacionesApi } from './invitaciones-api';
import { NotificacionesApi } from './notificaciones-api';
import { ReportesApi } from './reportes-api';
import { ReservasApi } from './reservas-api';
import { ServiciosApi } from './servicios-api';
import { UsuariosApi } from './usuarios-api';
import { NegocioActual, ORIGEN_NEGOCIO } from '../negocio/negocio-actual';
import { Observable } from 'rxjs';

describe('CG-5 · los servicios del negocio no usan rutas legadas', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ConfiguracionApi,
        EquipoApi,
        InvitacionesApi,
      ],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it.each(['norte', 'sur'])('cada recurso y el registro usan el slug explícito %s', (slug) => {
    TestBed.inject(NegocioActual).seleccionar(slug);
    const servicios = TestBed.inject(ServiciosApi);
    const barberos = TestBed.inject(BarberosApi);
    const horarios = TestBed.inject(HorariosApi);
    const reservas = TestBed.inject(ReservasApi);
    const avisos = TestBed.inject(NotificacionesApi);
    const equipo = TestBed.inject(EquipoApi);
    const invitaciones = TestBed.inject(InvitacionesApi);
    const usuarios = TestBed.inject(UsuariosApi);
    const configuracion = TestBed.inject(ConfiguracionApi);
    const reporte = TestBed.inject(ReportesApi);
    const inicio = '2026-10-01T10:00:00-05:00';
    const fecha = { desde: '2026-10-01', hasta: '2026-10-01' };
    const registro = {
      nombre: 'Cliente ficticio',
      correo: 'cliente@ejemplo.test',
      telefono: '999000001',
      password: 'Ficticia12345',
      aceptaPrivacidad: true,
    };
    const config = {
      nombre: 'Barbería ficticia',
      horizonteDias: 12,
      anticipacionCambioClienteMin: 120,
      limiteReservasActivas: 3,
      limiteBarberosActivos: 4,
      confirmacionManual: false,
      toleranciaInicioMin: 15,
    };
    const llamadas: Observable<unknown>[] = [
      TestBed.inject(AuthApi).registrar(registro),
      servicios.listar(),
      servicios.crear({ nombre: 'Corte', descripcion: 'Ficticio', duracionMin: 30, precio: 20 }),
      servicios.editar(1, {
        nombre: 'Corte',
        descripcion: 'Ficticio',
        duracionMin: 30,
        precio: 20,
      }),
      servicios.cambiarEstado(1, false),
      barberos.listar(),
      barberos.crear({ usuarioId: 1, especialidad: 'Corte' }),
      barberos.editar(1, { nombre: 'Nombre local', especialidad: 'Corte' }),
      barberos.cambiarEstado(1, false),
      horarios.jornadas(1),
      horarios.guardarSemana(1, []),
      horarios.bloqueos(1, fecha.desde, fecha.hasta),
      horarios.eliminarBloqueo(1),
      TestBed.inject(DisponibilidadApi).consultar({ servicioId: 1, fecha: fecha.desde }),
      reservas.obtener(1),
      reservas.mias({ pagina: 0, tamano: 20 }),
      reservas.agenda({ ...fecha, pagina: 0, tamano: 20 }),
      reservas.crear({ servicioId: 1, barberoId: 1, inicio }),
      reservas.reprogramar(1, { inicio, version: 0 }),
      reservas.cancelar(1, { version: 0 }),
      reservas.transicionar(1, { estado: 'CONFIRMADA', version: 0 }),
      reservas.auditoria(1),
      avisos.listar(),
      avisos.conteo(),
      avisos.marcarLeida(1),
      avisos.marcarTodas(),
      equipo.listar(),
      equipo.cambiarRol(1, 'BARBERO'),
      equipo.revocar(1),
      equipo.salir(),
      invitaciones.listar(),
      invitaciones.crear({ correo: 'persona@ejemplo.test', rol: 'BARBERO' }),
      invitaciones.revocar(1),
      configuracion.consultar(),
      configuracion.guardar(config),
      reporte.resumen(fecha),
      reporte.excel(fecha),
      usuarios.listar({ pagina: 0, tamano: 20 }),
      usuarios.restablecer(1),
      usuarios.cambiarEstado(1, false),
    ];
    llamadas.forEach((llamada) => llamada.subscribe());
    const solicitudes = TestBed.inject(HttpTestingController).match(() => true);
    expect(solicitudes).toHaveLength(llamadas.length);
    for (const solicitud of solicitudes) {
      expect(solicitud.request.url).toMatch(new RegExp('^/api/n/' + slug + '/'));
      expect(solicitud.request.url).not.toBe('/api/auth/registro');
      expect(solicitud.request.context.get(ORIGEN_NEGOCIO)?.slug).toBe(slug);
      solicitud.flush(solicitud.request.responseType === 'blob' ? new Blob() : {});
    }
  });

  it('el registro sin negocio falla antes de enviar ninguna petición', () => {
    expect(() =>
      TestBed.inject(AuthApi).registrar({
        nombre: 'Cliente ficticio',
        correo: 'cliente@ejemplo.test',
        telefono: '999000001',
        password: 'Ficticia12345',
        aceptaPrivacidad: true,
      }),
    ).toThrow('requiere una barbería');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });
});
