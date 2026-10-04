import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { NotificacionDto } from '../modelos/notificaciones';
import { Pagina } from '../modelos/pagina';

@Injectable({ providedIn: 'root' })
export class NotificacionesApi {
  private readonly http = inject(HttpClient);
  listar(pagina = 0, tamano = 20, soloNoLeidas = false) {
    return this.http.get<Pagina<NotificacionDto>>('/api/notificaciones', {
      params: { pagina, tamano, soloNoLeidas },
    });
  }
  conteo() {
    return this.http.get<{ noLeidas: number }>('/api/notificaciones/conteo');
  }
  marcarLeida(id: number) {
    return this.http.post<void>('/api/notificaciones/' + id + '/lectura', null);
  }
  marcarTodas() {
    return this.http.post<void>('/api/notificaciones/lectura', null);
  }
}
