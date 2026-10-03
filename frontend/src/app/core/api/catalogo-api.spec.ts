import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { BarberosApi } from './barberos-api';
import { ServiciosApi } from './servicios-api';

describe('APIs del catálogo administrativo', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  for (const recurso of ['servicios', 'barberos'] as const) {
    it.each([false, true])('lista ' + recurso + ' con incluirInactivos=%s', (inactivos) => {
      const api =
        recurso === 'servicios' ? TestBed.inject(ServiciosApi) : TestBed.inject(BarberosApi);
      let recibidos: unknown;
      const listado: Observable<unknown> = api.listar(inactivos);
      listado.subscribe((datos) => (recibidos = datos));
      const solicitud = http.expectOne('/api/' + recurso + '?incluirInactivos=' + inactivos);
      expect(solicitud.request.method).toBe('GET');
      solicitud.flush([{ id: 4, activo: !inactivos }]);
      expect(recibidos).toEqual([{ id: 4, activo: !inactivos }]);
    });
  }
  const servicio = {
    nombre: 'Corte ficticio',
    descripcion: 'Acabado',
    duracionMin: 30,
    precio: 25.5,
  };
  it('crea un servicio y conserva la respuesta', () => {
    TestBed.inject(ServiciosApi)
      .crear(servicio)
      .subscribe((dato) => expect(dato.id).toBe(9));
    const solicitud = http.expectOne('/api/servicios');
    expect(solicitud.request.method).toBe('POST');
    expect(solicitud.request.body).toEqual(servicio);
    solicitud.flush({ ...servicio, id: 9, activo: true });
  });
  it('edita un servicio por id', () => {
    TestBed.inject(ServiciosApi).editar(9, servicio).subscribe();
    const solicitud = http.expectOne('/api/servicios/9');
    expect(solicitud.request.method).toBe('PUT');
    expect(solicitud.request.body).toEqual(servicio);
    solicitud.flush({ ...servicio, id: 9, activo: true });
  });
  it.each([false, true])('envía estado de servicio %s', (activo) => {
    TestBed.inject(ServiciosApi).cambiarEstado(9, activo).subscribe();
    const solicitud = http.expectOne('/api/servicios/9/estado');
    expect(solicitud.request.method).toBe('PATCH');
    expect(solicitud.request.body).toEqual({ activo });
    solicitud.flush({ ...servicio, id: 9, activo });
  });
  it.each([
    {
      nombre: 'Barbero ficticio',
      correo: 'barbero@ejemplo.test',
      telefono: null,
      especialidad: 'Cortes',
      passwordTemporal: 'Ficticia123',
    },
    { usuarioId: 14, especialidad: 'Cortes' },
  ])('crea cuenta o vínculo sin alterar su variante', (datos) => {
    TestBed.inject(BarberosApi).crear(datos).subscribe();
    const solicitud = http.expectOne('/api/barberos');
    expect(solicitud.request.method).toBe('POST');
    expect(solicitud.request.body).toEqual(datos);
    solicitud.flush({ id: 11 });
  });
  it('edita barbero sin mandar correo ni contraseña', () => {
    const datos = { nombre: 'Nombre actualizado', telefono: '999000017', especialidad: 'Barba' };
    TestBed.inject(BarberosApi).editar(11, datos).subscribe();
    const solicitud = http.expectOne('/api/barberos/11');
    expect(solicitud.request.method).toBe('PUT');
    expect(solicitud.request.body).toEqual(datos);
    solicitud.flush({ ...datos, id: 11 });
  });
  it.each([false, true])('envía estado de barbero %s y conserva reservas futuras', (activo) => {
    TestBed.inject(BarberosApi)
      .cambiarEstado(11, activo)
      .subscribe((respuesta) => expect(respuesta.reservasFuturasVigentes).toBe(3));
    const solicitud = http.expectOne('/api/barberos/11/estado');
    expect(solicitud.request.method).toBe('PATCH');
    expect(solicitud.request.body).toEqual({ activo });
    solicitud.flush({ barbero: { id: 11, activo }, reservasFuturasVigentes: 3 });
  });
});
