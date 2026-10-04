import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Pagina } from '../modelos/pagina';
import {
  CancelarReservaDto,
  ConsultaMisReservas,
  CrearReservaDto,
  ReprogramarReservaDto,
  ReservaDto,
} from '../modelos/reservas';

@Injectable({ providedIn: 'root' })
export class ReservasApi {
  private readonly http = inject(HttpClient);
  obtener(id: number) {
    return this.http.get<ReservaDto>('/api/reservas/' + id);
  }
  mias(consulta: ConsultaMisReservas) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta)) {
      if (valor !== undefined && valor !== '') params = params.set(clave, valor);
    }
    return this.http.get<Pagina<ReservaDto>>('/api/reservas/mias', { params });
  }
  crear(datos: CrearReservaDto) {
    return this.http.post<ReservaDto>('/api/reservas', datos);
  }
  reprogramar(id: number, datos: ReprogramarReservaDto) {
    return this.http.post<ReservaDto>('/api/reservas/' + id + '/reprogramacion', datos);
  }
  cancelar(id: number, datos: CancelarReservaDto) {
    return this.http.post<ReservaDto>('/api/reservas/' + id + '/cancelacion', datos);
  }
}
