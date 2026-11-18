import { NegocioActual } from '../negocio/negocio-actual';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { NotificacionDto } from '../modelos/notificaciones';
import { Pagina } from '../modelos/pagina';

@Injectable({ providedIn: 'root' })
export class NotificacionesApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  listar(pagina = 0, tamano = 20, soloNoLeidas = false) {
    return this.http.get<Pagina<NotificacionDto>>(this.negocio.url('/notificaciones'), {
      context: this.negocio.contexto(),
      params: { pagina, tamano, soloNoLeidas },
    });
  }
  conteo() {
    return this.http.get<{ noLeidas: number }>(this.negocio.url('/notificaciones/conteo'), {
      context: this.negocio.contexto(),
    });
  }
  marcarLeida(id: number) {
    return this.http.post<void>(this.negocio.url('/notificaciones/') + id + '/lectura', null, {
      context: this.negocio.contexto(),
    });
  }
  marcarTodas() {
    return this.http.post<void>(this.negocio.url('/notificaciones/lectura'), null, {
      context: this.negocio.contexto(),
    });
  }
}
