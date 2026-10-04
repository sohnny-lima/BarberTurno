import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Pagina } from '../modelos/pagina';
import {
  AuditoriaDto,
  ConsultaAgenda,
  TransicionReservaDto,
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
  agenda(consulta: ConsultaAgenda) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta)) {
      if (valor !== undefined) params = params.set(clave, valor);
    }
    return this.http.get<Pagina<ReservaDto>>('/api/reservas', { params });
  }
  transicionar(id: number, datos: TransicionReservaDto) {
    return this.http.post<ReservaDto>('/api/reservas/' + id + '/transiciones', datos);
  }
  auditoria(id: number) {
    return this.http.get<AuditoriaDto[]>('/api/reservas/' + id + '/auditoria');
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
