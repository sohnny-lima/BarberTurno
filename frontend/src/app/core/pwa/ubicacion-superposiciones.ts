import { LocationChangeListener, PathLocationStrategy } from '@angular/common';
import { inject, Injectable } from '@angular/core';

/** Filtra únicamente recorridos del historial que pertenecen a una superposición del shell. */
@Injectable({ providedIn: 'root' })
export class FiltroSuperposiciones {
  consumir: (evento: PopStateEvent) => boolean = () => false;
}

/** Conserva las rutas normales y evita entregar al router una retirada de entrada propia. */
@Injectable()
export class UbicacionSuperposiciones extends PathLocationStrategy {
  private readonly filtro = inject(FiltroSuperposiciones);

  override onPopState(escuchar: LocationChangeListener): void {
    super.onPopState((evento) => {
      if (evento.type !== 'popstate' || !this.filtro.consumir(evento as PopStateEvent))
        escuchar(evento);
    });
  }
}
