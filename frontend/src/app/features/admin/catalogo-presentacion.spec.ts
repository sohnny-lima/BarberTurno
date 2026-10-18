import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { By } from '@angular/platform-browser';
import { BehaviorSubject, Subject } from 'rxjs';
import {
  ConfirmarEstadoDatos,
  ConfirmarEstadoDialogo,
} from '../../shared/confirmar-estado-dialogo';
import { BarberoDialogo } from './barbero-dialogo';
import { Barberos } from './barberos';
import { ServicioDialogo } from './servicio-dialogo';
import { Servicios } from './servicios';

registerLocaleData(localeEsPe);

for (const config of [
  {
    recurso: 'servicios',
    singular: 'servicio',
    titulo: 'Servicios',
    componente: Servicios,
    dialogo: ServicioDialogo,
    fila: {
      id: 7,
      nombre: 'Barba',
      descripcion: 'Perfilado de barba',
      duracionMin: 20,
      precio: 20,
      activo: true,
    },
    datos: ['Perfilado de barba', '20 min', 'S/20.00'],
  },
  {
    recurso: 'barberos',
    singular: 'barbero',
    titulo: 'Barberos',
    componente: Barberos,
    dialogo: BarberoDialogo,
    fila: {
      id: 7,
      nombre: 'Profesional ficticio',
      especialidad: 'Corte y barba',
      correo: 'personal@ejemplo.test',
      telefono: '999000007',
      activo: true,
    },
    datos: ['Corte y barba', 'personal@ejemplo.test', '999000007'],
  },
]) {
  describe('Presentación de ' + config.recurso, () => {
    let http: HttpTestingController;
    let cambios: BehaviorSubject<BreakpointState>;
    let cierre: Subject<boolean>;
    const observe = vi.fn();
    const dialogos = { open: vi.fn() };
    const url = '/api/' + config.recurso;
    beforeEach(() => {
      cambios = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
      cierre = new Subject<boolean>();
      observe.mockReset().mockReturnValue(cambios);
      dialogos.open.mockReset().mockReturnValue({ afterClosed: () => cierre });
      TestBed.configureTestingModule({
        imports: [config.componente],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: BreakpointObserver, useValue: { observe } },
          { provide: MatDialog, useValue: dialogos },
        ],
      });
      http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());
    function preparar() {
      const f = TestBed.createComponent(config.componente as Type<Servicios | Barberos>);
      http
        .expectOne(url + '?incluirInactivos=false')
        .flush([
          config.fila,
          { ...config.fila, id: 8, nombre: 'Inactivo ficticio', activo: false },
        ]);
      f.detectChanges();
      return f;
    }
    it('solo instancia tabla desde 1200 px o tarjetas con los mismos datos, acciones, riesgo y nombres', () => {
      const f = preparar();
      const editar = vi.spyOn(f.componentInstance, 'editar').mockImplementation(() => undefined);
      const estado = vi
        .spyOn(f.componentInstance, 'cambiarEstado')
        .mockImplementation(() => undefined);
      for (const ancho of [360, 1024, 1199, 1200, 1440, 1199]) {
        const escritorio = ancho >= 1200;
        cambios.next({ matches: escritorio, breakpoints: { '(min-width: 1200px)': escritorio } });
        f.detectChanges();
        const raiz: HTMLElement = f.nativeElement;
        expect(raiz.querySelectorAll('table')).toHaveLength(escritorio ? 1 : 0);
        expect(raiz.querySelectorAll('[role=list]')).toHaveLength(escritorio ? 0 : 1);
        expect(raiz.querySelectorAll('[role=listitem]')).toHaveLength(escritorio ? 0 : 2);
        expect(
          raiz.querySelector(escritorio ? 'table' : '[role=list]')?.getAttribute('aria-label'),
        ).toBe(config.titulo);
        expect(raiz.querySelector('[role=region]')).toBeNull();
        expect(raiz.textContent).not.toContain('desplace la tabla');
        for (const dato of config.datos)
          expect(raiz.textContent?.replace(/\s/g, '')).toContain(dato.replace(/\s/g, ''));
        for (const [nombre, riesgo] of [
          ['Editar ' + config.singular + ' ' + config.fila.nombre, false],
          ['Desactivar ' + config.fila.nombre, true],
          ['Activar Inactivo ficticio', false],
        ] as const) {
          const botones = raiz.querySelectorAll<HTMLButtonElement>('[aria-label="' + nombre + '"]');
          expect(botones).toHaveLength(1);
          expect(botones[0].disabled).toBe(false);
          expect(botones[0].classList.contains('riesgo')).toBe(riesgo);
          botones[0].click();
        }
        expect(raiz.querySelectorAll('.inactivo')).toHaveLength(1);
      }
      expect(editar).toHaveBeenCalledTimes(6);
      expect(editar).toHaveBeenCalledWith(config.fila);
      expect(estado).toHaveBeenCalledTimes(12);
      expect(estado).toHaveBeenCalledWith(config.fila);
      expect(estado).toHaveBeenCalledWith(expect.objectContaining({ id: 8, activo: false }));
      expect(observe).toHaveBeenCalledWith('(min-width: 1200px)');
      f.destroy();
      expect(cambios.observed).toBe(false);
    });
    it('anuncia carga, conserva el error y permite reintentar hasta el vacío con la acción existente', () => {
      const f = TestBed.createComponent(config.componente as Type<Servicios | Barberos>);
      f.detectChanges();
      const raiz: HTMLElement = f.nativeElement;
      expect(raiz.querySelector('[role=status]')?.textContent).toBe(
        'Cargando ' + config.recurso + '…',
      );
      expect(raiz.querySelectorAll('table,[role=list]')).toHaveLength(0);
      expect(raiz.querySelector<HTMLButtonElement>('app-cabecera-pagina button')?.disabled).toBe(
        true,
      );
      http
        .expectOne(url + '?incluirInactivos=false')
        .flush(
          { detail: 'Mensaje actual del servidor.' },
          { status: 400, statusText: 'Bad Request' },
        );
      f.detectChanges();
      expect(raiz.querySelector('[role=alert]')?.textContent).toContain(
        'Mensaje actual del servidor.',
      );
      raiz.querySelector<HTMLButtonElement>('[role=alert] button')!.click();
      http.expectOne(url + '?incluirInactivos=false').flush([]);
      f.detectChanges();
      expect(raiz.querySelector('[role=alert]')).toBeNull();
      expect(raiz.querySelector('[role=status]')?.textContent).toContain(
        'No hay ' + config.recurso + ' para mostrar.',
      );
      raiz.querySelector<HTMLButtonElement>('app-estado-contenido button')!.click();
      expect(dialogos.open).toHaveBeenCalledWith(
        config.dialogo,
        expect.objectContaining({ data: null }),
      );
    });
    it('conserva el filtro, Actualizar y la edición que recarga solo al guardar', () => {
      const f = preparar();
      const raiz: HTMLElement = f.nativeElement;
      const toggle = f.debugElement.query(By.directive(MatSlideToggle))
        .componentInstance as MatSlideToggle;
      toggle.change.emit({ source: toggle, checked: true });
      f.componentInstance.cargar();
      http.expectOne(url + '?incluirInactivos=true').flush([config.fila]);
      f.detectChanges();
      raiz.querySelector<HTMLButtonElement>('.herramientas > button')!.click();
      http.expectOne(url + '?incluirInactivos=true').flush([config.fila]);
      f.detectChanges();
      raiz
        .querySelector<HTMLButtonElement>(
          '[aria-label="Editar ' + config.singular + ' ' + config.fila.nombre + '"]',
        )!
        .click();
      expect(dialogos.open).toHaveBeenLastCalledWith(
        config.dialogo,
        expect.objectContaining({ data: config.fila, maxHeight: 'calc(100dvh - 32px)' }),
      );
      cierre.next(false);
      http.expectNone((r) => r.url === url);
      cierre.next(true);
      http.expectOne(url + '?incluirInactivos=true').flush([config.fila]);
    });
    it.each([true, false])(
      'declara riesgo solo al desactivar; activo = %s, con la misma petición',
      (activo) => {
        const f = TestBed.createComponent(config.componente as Type<Servicios | Barberos>);
        const fila = { ...config.fila, activo };
        http.expectOne(url + '?incluirInactivos=false').flush([fila]);
        f.detectChanges();
        f.nativeElement
          .querySelector(
            '[aria-label="' + (activo ? 'Desactivar ' : 'Activar ') + fila.nombre + '"]',
          )
          .click();
        expect(dialogos.open.mock.calls[0][0]).toBe(ConfirmarEstadoDialogo);
        const datos = dialogos.open.mock.calls[0][1].data as ConfirmarEstadoDatos;
        expect(datos.activar).toBe(!activo);
        expect(datos.riesgo).toBe(activo);
        let reservas: number | undefined;
        datos.cambiar().subscribe((n) => (reservas = n));
        const peticion = http.expectOne(url + '/7/estado');
        expect(peticion.request.method).toBe('PATCH');
        expect(peticion.request.body).toEqual({ activo: !activo });
        peticion.flush(
          config.recurso === 'barberos' ? { barbero: fila, reservasFuturasVigentes: 3 } : fila,
        );
        expect(reservas).toBe(config.recurso === 'barberos' ? 3 : 0);
        cierre.next(true);
        http.expectOne(url + '?incluirInactivos=false').flush([fila]);
      },
    );
  });
}
