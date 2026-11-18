import { NegocioActual } from '../negocio/negocio-actual';
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
  private readonly negocio = inject(NegocioActual).cliente();
  obtener(id: number) {
    return this.http.get<ReservaDto>(this.negocio.url('/reservas/') + id, {
      context: this.negocio.contexto(),
    });
  }
  mias(consulta: ConsultaMisReservas) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta)) {
      if (valor !== undefined && valor !== '') params = params.set(clave, valor);
    }
    return this.http.get<Pagina<ReservaDto>>(this.negocio.url('/reservas/mias'), {
      params,
      context: this.negocio.contexto(),
    });
  }
  agenda(consulta: ConsultaAgenda) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta)) {
      if (valor !== undefined) params = params.set(clave, valor);
    }
    return this.http.get<Pagina<ReservaDto>>(this.negocio.url('/reservas'), {
      params,
      context: this.negocio.contexto(),
    });
  }
  transicionar(id: number, datos: TransicionReservaDto) {
    return this.http.post<ReservaDto>(
      this.negocio.url('/reservas/') + id + '/transiciones',
      datos,
      { context: this.negocio.contexto() },
    );
  }
  auditoria(id: number) {
    return this.http.get<AuditoriaDto[]>(this.negocio.url('/reservas/') + id + '/auditoria', {
      context: this.negocio.contexto(),
    });
  }
  crear(datos: CrearReservaDto) {
    return this.http.post<ReservaDto>(this.negocio.url('/reservas'), datos, {
      context: this.negocio.contexto(),
    });
  }
  reprogramar(id: number, datos: ReprogramarReservaDto) {
    return this.http.post<ReservaDto>(
      this.negocio.url('/reservas/') + id + '/reprogramacion',
      datos,
      { context: this.negocio.contexto() },
    );
  }
  cancelar(id: number, datos: CancelarReservaDto) {
    return this.http.post<ReservaDto>(this.negocio.url('/reservas/') + id + '/cancelacion', datos, {
      context: this.negocio.contexto(),
    });
  }
}
