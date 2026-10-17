import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatButton } from '@angular/material/button';
import { By } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { AccionDialogo } from './accion-dialogo';
import { CabeceraPagina } from './cabecera-pagina';
import { ComposicionAcceso } from './composicion-acceso';
import { EstadoContenido } from './estado-contenido';
import { ListaAdaptable } from './lista-adaptable';
import { SeccionFormulario } from './seccion-formulario';
import { TituloPagina } from './titulo-pagina';

@Component({
  imports: [ListaAdaptable],
  template: `<app-lista-adaptable nombre="Servicios" [tabla]="tabla" [tarjetas]="tarjetas" />
    <ng-template #tabla>
      <table aria-label="Servicios">
        <tbody>
          <tr>
            <td>Barba</td>
            <td>
              <button aria-label="Editar servicio Barba" (click)="editar()">Editar</button>
              @if (puedeDesactivar) {
                <button aria-label="Desactivar servicio Barba" (click)="desactivar()">
                  Desactivar
                </button>
              }
            </td>
          </tr>
        </tbody>
      </table>
    </ng-template>
    <ng-template #tarjetas>
      <article role="listitem">
        Barba
        <button aria-label="Editar servicio Barba" (click)="editar()">Editar</button>
        @if (puedeDesactivar) {
          <button aria-label="Desactivar servicio Barba" (click)="desactivar()">Desactivar</button>
        }
      </article>
    </ng-template>`,
})
class ListaPrueba {
  readonly editar = vi.fn();
  readonly desactivar = vi.fn();
  puedeDesactivar = true;
}

@Component({
  imports: [CabeceraPagina],
  template: `<app-cabecera-pagina titulo="Servicios" subtitulo="Catálogo">
    <button (click)="crear()">Nuevo servicio</button>
  </app-cabecera-pagina>`,
})
class CabeceraPrueba {
  readonly crear = vi.fn();
}

describe('Patrones de presentación verde', () => {
  it('conserva un único h1 y comunica el título a la barra móvil', () => {
    const f = TestBed.createComponent(CabeceraPagina);
    f.componentRef.setInput('titulo', 'Mi cuenta');
    f.componentRef.setInput('subtitulo', 'Actualice su nombre y teléfono.');
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(f.nativeElement.querySelector('h1').textContent).toBe('Mi cuenta');
    expect(f.nativeElement.querySelector('header').classList.contains('titulo-movil')).toBe(true);
    expect(TestBed.inject(TituloPagina).texto()).toBe('Mi cuenta');
    f.componentRef.setInput('ocultarEnMovil', false);
    f.detectChanges();
    expect(f.nativeElement.querySelector('header').classList.contains('titulo-movil')).toBe(false);
    f.destroy();
    expect(TestBed.inject(TituloPagina).texto()).toBeNull();
  });

  it('proyecta una sola acción principal después del título y subtítulo', () => {
    const f = TestBed.createComponent(CabeceraPrueba);
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(f.nativeElement.querySelectorAll('button')).toHaveLength(1);
    const accion = f.nativeElement.querySelector('.accion button');
    expect(accion.textContent).toBe('Nuevo servicio');
    accion.click();
    expect(f.componentInstance.crear).toHaveBeenCalledOnce();
  });

  it('anuncia la carga una vez y mantiene los esqueletos decorativos', () => {
    const f = TestBed.createComponent(EstadoContenido);
    f.componentRef.setInput('tipo', 'carga');
    f.componentRef.setInput('texto', 'Cargando barberos…');
    f.componentRef.setInput('forma', 'formulario');
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('[role=status]')).toHaveLength(1);
    expect(f.nativeElement.querySelector('[role=status]').textContent).toBe('Cargando barberos…');
    expect(f.nativeElement.querySelector('.esqueletos').getAttribute('aria-hidden')).toBe('true');
    expect(f.nativeElement.querySelectorAll('.formulario span')).toHaveLength(3);
  });

  it('contiene el reintento dentro de la alerta y emite solo al pulsarlo', () => {
    const f = TestBed.createComponent(EstadoContenido);
    f.componentRef.setInput('tipo', 'error');
    f.componentRef.setInput('texto', 'No se pudo cargar.');
    f.componentRef.setInput('reintento', 'Reintentar');
    const reintentar = vi.fn();
    f.componentInstance.reintentar.subscribe(reintentar);
    f.detectChanges();
    expect(reintentar).not.toHaveBeenCalled();
    const alerta = f.nativeElement.querySelector('[role=alert]');
    expect(alerta.textContent).toContain('No se pudo cargar.');
    alerta.querySelector('button').click();
    expect(reintentar).toHaveBeenCalledOnce();
  });

  it.each(['vacio', 'aviso'])('mantiene role status para %s', (tipo) => {
    const f = TestBed.createComponent(EstadoContenido);
    f.componentRef.setInput('tipo', tipo);
    f.componentRef.setInput('texto', 'Texto actual.');
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role=status]').textContent).toBe('Texto actual.');
    expect(f.nativeElement.querySelector('[role=alert]')).toBeNull();
    expect(f.nativeElement.querySelector('button')).toBeNull();
  });

  it('presenta el h2 de sección sin añadir otro h1', () => {
    const f = TestBed.createComponent(SeccionFormulario);
    f.componentRef.setInput('titulo', 'Datos personales');
    f.detectChanges();
    expect(f.nativeElement.querySelector('h2').textContent).toBe('Datos personales');
    expect(f.nativeElement.querySelector('h1')).toBeNull();
  });

  it('la foto de acceso es decorativa, local y sin carga diferida', () => {
    const f = TestBed.createComponent(ComposicionAcceso);
    f.componentRef.setInput('registro', true);
    f.detectChanges();
    const foto = f.nativeElement.querySelector('img');
    expect(foto.getAttribute('alt')).toBe('');
    expect(foto.getAttribute('src')).toBe('fotos/interior-vacio.webp');
    expect(foto.hasAttribute('loading')).toBe(false);
    expect(f.nativeElement.querySelector('.registro')).not.toBeNull();
  });

  it('la acción principal pasa de relleno a contorno de riesgo por un dato explícito', () => {
    const f = TestBed.createComponent(AccionDialogo);
    const confirmar = vi.fn();
    f.componentInstance.confirmar.subscribe(confirmar);
    f.detectChanges();
    expect(f.debugElement.query(By.directive(MatButton)).componentInstance.appearance).toBe(
      'filled',
    );
    f.componentRef.setInput('riesgo', true);
    f.detectChanges();
    const boton: HTMLButtonElement = f.nativeElement.querySelector('button');
    expect(boton.classList.contains('riesgo')).toBe(true);
    expect(f.debugElement.query(By.directive(MatButton)).componentInstance.appearance).toBe(
      'outlined',
    );
    boton.click();
    expect(confirmar).toHaveBeenCalledOnce();
    f.componentRef.setInput('desactivada', true);
    f.detectChanges();
    boton.click();
    expect(boton.disabled).toBe(true);
    expect(confirmar).toHaveBeenCalledOnce();
  });

  it('solo instancia tabla o tarjetas, con las mismas acciones, condiciones y nombres accesibles', () => {
    const cambios = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
    const observe = vi.fn(() => cambios);
    TestBed.configureTestingModule({
      providers: [{ provide: BreakpointObserver, useValue: { observe } }],
    });
    const f = TestBed.createComponent(ListaPrueba);
    f.detectChanges();
    expect(observe).toHaveBeenCalledWith('(min-width: 1200px)');
    for (const escritorio of [false, true, false]) {
      cambios.next({ matches: escritorio, breakpoints: {} });
      f.detectChanges();
      expect(f.nativeElement.querySelectorAll('table')).toHaveLength(escritorio ? 1 : 0);
      expect(f.nativeElement.querySelectorAll('[role=list]')).toHaveLength(escritorio ? 0 : 1);
      if (!escritorio)
        expect(f.nativeElement.querySelector('[role=list]').getAttribute('aria-label')).toBe(
          'Servicios',
        );
      const editar = f.nativeElement.querySelectorAll('[aria-label="Editar servicio Barba"]');
      const desactivar = f.nativeElement.querySelectorAll(
        '[aria-label="Desactivar servicio Barba"]',
      );
      expect(editar).toHaveLength(1);
      expect(desactivar).toHaveLength(1);
      editar[0].click();
      desactivar[0].click();
      f.componentInstance.puedeDesactivar = false;
      f.detectChanges();
      expect(
        f.nativeElement.querySelectorAll('[aria-label="Desactivar servicio Barba"]'),
      ).toHaveLength(0);
      f.componentInstance.puedeDesactivar = true;
      f.detectChanges();
    }
    expect(f.componentInstance.editar).toHaveBeenCalledTimes(3);
    expect(f.componentInstance.desactivar).toHaveBeenCalledTimes(3);
    f.destroy();
    expect(cambios.observed).toBe(false);
  });
});
