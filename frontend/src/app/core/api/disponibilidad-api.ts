import { NegocioActual } from '../negocio/negocio-actual';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ConsultaDisponibilidad, DisponibilidadDto } from '../modelos/reservas';

@Injectable({ providedIn: 'root' })
export class DisponibilidadApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  consultar(consulta: ConsultaDisponibilidad) {
    const params: Record<string, string | number> = {
      servicioId: consulta.servicioId,
      fecha: consulta.fecha,
    };
    if (consulta.barberoId !== undefined) params['barberoId'] = consulta.barberoId;
    if (consulta.excluirReservaId !== undefined)
      params['excluirReservaId'] = consulta.excluirReservaId;
    return this.http.get<DisponibilidadDto>(this.negocio.url('/disponibilidad'), {
      params,
      context: this.negocio.contexto(),
    });
  }
}
