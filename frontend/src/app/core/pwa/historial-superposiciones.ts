import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable } from '@angular/core';

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
  private secuencia = 0;
  private retirando = false;
  private procesando = false;
  private readonly pendientes: (() => void)[] = [];

  constructor() {
    const escuchar = (evento: PopStateEvent) => {
      const superior = this.entradas.at(-1);
      if (this.retirando) {
        evento.stopImmediatePropagation();
        this.retirando = false;
        this.depurar();
        return;
      }
      if (superior?.activa) {
        evento.stopImmediatePropagation();
        this.entradas.pop();
        superior.activa = false;
        this.procesando = true;
        superior.cerrar();
        this.procesando = false;
        this.depurar();
      } else if (evento.state?.[MARCA]) {
        // Adelante no resucita una superposición ya cerrada.
        const { [MARCA]: omitida, ...estado } = evento.state;
        void omitida;
        this.ventana.history.replaceState(estado, '');
      }
    };
    this.ventana.addEventListener('popstate', escuchar, true);
    inject(DestroyRef).onDestroy(() =>
      this.ventana.removeEventListener('popstate', escuchar, true),
    );
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
    const agregar = () => {
      if (!entrada.activa) return;
      this.entradas.push(entrada);
      this.ventana.history.pushState({ ...this.ventana.history.state, [MARCA]: entrada.id }, '');
    };
    if (this.retirando) this.pendientes.push(agregar);
    else agregar();
    return () => {
      if (!entrada.activa) return;
      entrada.activa = false;
      if (!this.procesando) this.depurar();
    };
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
      this.depurar();
      return;
    }
    this.pendientes.splice(0).forEach((abrir) => abrir());
  }
}
