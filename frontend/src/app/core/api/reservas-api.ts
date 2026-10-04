import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { CrearReservaDto, ReprogramarReservaDto, ReservaDto } from '../modelos/reservas';

@Injectable({ providedIn: 'root' })
export class ReservasApi {
  private readonly http = inject(HttpClient);
  obtener(id: number) {
    return this.http.get<ReservaDto>('/api/reservas/' + id);
  }
  crear(datos: CrearReservaDto) {
    return this.http.post<ReservaDto>('/api/reservas', datos);
  }
  reprogramar(id: number, datos: ReprogramarReservaDto) {
    return this.http.post<ReservaDto>('/api/reservas/' + id + '/reprogramacion', datos);
  }
}
