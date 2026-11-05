import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable } from '@angular/core';
import { FiltroSuperposiciones } from './ubicacion-superposiciones';

const MARCA = 'barberturnoSuperposicion';
interface Entrada {
  id: number;
  cerrar: () => void;
  activa: boolean;
}

/** Retira entradas propias capturando popstate antes del router, sin navegar ni duplicar rutas. */
@Injectable({ providedIn: 'root' })
export class HistorialSuperposiciones {
  private readonly ventana = inject(DOCUMENT).defaultView!;
  private readonly entradas: Entrada[] = [];
  private readonly retiradas = new Set<number>();
  private ultimaRuta = this.ventana.location.href;
  private secuencia = 0;
  private retirando = false;
  private procesando = false;
  private readonly pendientes: Entrada[] = [];
  private readonly esperas: (() => void)[] = [];

  constructor() {
    const filtro = inject(FiltroSuperposiciones);
    const consumidos = new WeakMap<PopStateEvent, boolean>();
    const escuchar = (evento: PopStateEvent) => {
      const consumido = consumidos.get(evento);
      if (consumido !== undefined) return consumido;
      const superior = this.entradas.at(-1);
      if (this.retirando) {
        consumidos.set(evento, true);
        evento.stopImmediatePropagation();
        this.retirando = false;
        this.depurar();
        return true;
      }
      if (superior?.activa) {
        consumidos.set(evento, true);
        evento.stopImmediatePropagation();
        this.entradas.pop();
        superior.activa = false;
        this.procesando = true;
        superior.cerrar();
        this.procesando = false;
        this.depurar();
        return true;
      } else if (this.retiradas.has(evento.state?.[MARCA])) {
        consumidos.set(evento, true);
        // Una navegación desde el menú dejó su entrada debajo de la nueva ruta.
        // Se salta esa copia, también al avanzar, antes de que la vea el router.
        evento.stopImmediatePropagation();
        this.ventana.history.go(this.ultimaRuta === this.ventana.location.href ? 1 : -1);
        return true;
      } else if (evento.state?.[MARCA]) {
        // Adelante no resucita una superposición ya cerrada.
        const { [MARCA]: omitida, ...estado } = evento.state;
        void omitida;
        this.ventana.history.replaceState(estado, '');
      }
      consumidos.set(evento, false);
      return false;
    };
    filtro.consumir = escuchar;
    this.ventana.addEventListener('popstate', escuchar, true);
    inject(DestroyRef).onDestroy(() => {
      this.ventana.removeEventListener('popstate', escuchar, true);
      filtro.consumir = () => false;
    });
  }

  navegacionTerminada() {
    this.ultimaRuta = this.ventana.location.href;
  }

  /** La salida de una cuenta espera a retirar el historial antes de sustituir la ruta. */
  cerrarTodas(): Promise<void> {
    const pendientes = this.pendientes.splice(0);
    for (const entrada of [...this.entradas, ...pendientes].reverse()) {
      if (entrada.activa) {
        entrada.activa = false;
        entrada.cerrar();
      }
    }
    this.depurar();
    return this.retirando
      ? new Promise((resolver) => this.esperas.push(resolver))
      : Promise.resolve();
  }

  /** Material no expone un flujo global de menús: se observan los paneles accesibles. */
  vigilarMenus(destruccion: DestroyRef) {
    const documento = this.ventana.document;
    const paneles = new Map<Element, () => void>();
    const observar = () => {
      for (const [panel, retirar] of paneles) {
        if (!panel.isConnected) {
          paneles.delete(panel);
          retirar();
        }
      }
      for (const panel of documento.querySelectorAll('.cdk-overlay-container [role="menu"]')) {
        if (!paneles.has(panel))
          paneles.set(
            panel,
            this.abrir(() => {
              panel.dispatchEvent(
                new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }),
              );
            }),
          );
      }
    };
    const observer = new MutationObserver(observar);
    observer.observe(documento.body, { childList: true, subtree: true });
    destruccion.onDestroy(() => {
      observer.disconnect();
      paneles.forEach((retirar) => retirar());
    });
  }

  abrir(cerrar: () => void): () => void {
    const entrada: Entrada = { id: ++this.secuencia, cerrar, activa: true };
    if (this.retirando) this.pendientes.push(entrada);
    else this.agregar(entrada);
    return () => {
      if (!entrada.activa) return;
      entrada.activa = false;
      if (!this.procesando) this.depurar();
    };
  }

  private agregar(entrada: Entrada) {
    if (!entrada.activa) return;
    this.entradas.push(entrada);
    this.ventana.history.pushState({ ...this.ventana.history.state, [MARCA]: entrada.id }, '');
  }

  private depurar() {
    if (this.retirando) return;
    const superior = this.entradas.at(-1);
    if (superior && !superior.activa) {
      this.entradas.pop();
      // Nunca retrocede una entrada del router: solo la propia que siga en la cima.
      if (this.ventana.history.state?.[MARCA] === superior.id) {
        this.retirando = true;
        this.ventana.history.back();
        return;
      }
      this.retiradas.add(superior.id);
      this.depurar();
      return;
    }
    this.pendientes.splice(0).forEach((entrada) => this.agregar(entrada));
    this.esperas.splice(0).forEach((resolver) => resolver());
  }
}
