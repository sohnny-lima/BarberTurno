import { NegocioActual } from '../negocio/negocio-actual';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BloqueoDto, BloqueoLoteDto, CrearBloqueoDto, JornadaDto } from '../modelos/horarios';

@Injectable({ providedIn: 'root' })
export class HorariosApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  jornadas(id: number) {
    return this.http.get<JornadaDto[]>(this.negocio.url('/barberos/') + id + '/jornadas', {
      context: this.negocio.contexto(),
    });
  }
  guardarSemana(id: number, semana: JornadaDto[]) {
    return this.http.put<JornadaDto[]>(this.negocio.url('/barberos/') + id + '/jornadas', semana, {
      context: this.negocio.contexto(),
    });
  }
  bloqueos(id: number, desde: string, hasta: string) {
    return this.http.get<BloqueoDto[]>(this.negocio.url('/barberos/') + id + '/bloqueos', {
      context: this.negocio.contexto(),
      params: { desde, hasta },
    });
  }
  crearBloqueo(id: number, datos: CrearBloqueoDto) {
    return this.http.post<BloqueoDto>(this.negocio.url('/barberos/') + id + '/bloqueos', datos, {
      context: this.negocio.contexto(),
    });
  }
  crearLote(datos: BloqueoLoteDto) {
    return this.http.post<BloqueoDto[]>(this.negocio.url('/bloqueos/lote'), datos, {
      context: this.negocio.contexto(),
    });
  }
  eliminarBloqueo(id: number) {
    return this.http.delete<void>(this.negocio.url('/bloqueos/') + id, {
      context: this.negocio.contexto(),
    });
  }
}
