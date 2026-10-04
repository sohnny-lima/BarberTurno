import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BloqueoDto, BloqueoLoteDto, CrearBloqueoDto, JornadaDto } from '../modelos/horarios';

@Injectable({ providedIn: 'root' })
export class HorariosApi {
  private readonly http = inject(HttpClient);
  jornadas(id: number) {
    return this.http.get<JornadaDto[]>('/api/barberos/' + id + '/jornadas');
  }
  guardarSemana(id: number, semana: JornadaDto[]) {
    return this.http.put<JornadaDto[]>('/api/barberos/' + id + '/jornadas', semana);
  }
  bloqueos(id: number, desde: string, hasta: string) {
    return this.http.get<BloqueoDto[]>('/api/barberos/' + id + '/bloqueos', {
      params: { desde, hasta },
    });
  }
  crearBloqueo(id: number, datos: CrearBloqueoDto) {
    return this.http.post<BloqueoDto>('/api/barberos/' + id + '/bloqueos', datos);
  }
  crearLote(datos: BloqueoLoteDto) {
    return this.http.post<BloqueoDto[]>('/api/bloqueos/lote', datos);
  }
  eliminarBloqueo(id: number) {
    return this.http.delete<void>('/api/bloqueos/' + id);
  }
}
