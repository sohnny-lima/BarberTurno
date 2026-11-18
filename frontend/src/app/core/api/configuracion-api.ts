import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ConfiguracionNegocioDto } from '../modelos/gestion';
import { NegocioActual } from '../negocio/negocio-actual';

@Injectable()
export class ConfiguracionApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  consultar() {
    return this.http.get<ConfiguracionNegocioDto>(this.negocio.url('/configuracion'), {
      context: this.negocio.contexto(),
    });
  }
  guardar(datos: ConfiguracionNegocioDto) {
    return this.http.put<ConfiguracionNegocioDto>(this.negocio.url('/configuracion'), datos, {
      context: this.negocio.contexto(),
    });
  }
}
