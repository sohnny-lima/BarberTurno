import { NegocioActual } from '../negocio/negocio-actual';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { GuardarServicioDto, ServicioDto } from '../modelos/catalogo';

@Injectable({ providedIn: 'root' })
export class ServiciosApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  listar(incluirInactivos = false) {
    return this.http.get<ServicioDto[]>(this.negocio.url('/servicios'), {
      context: this.negocio.contexto(),
      params: { incluirInactivos },
    });
  }
  crear(datos: GuardarServicioDto) {
    return this.http.post<ServicioDto>(this.negocio.url('/servicios'), datos, {
      context: this.negocio.contexto(),
    });
  }
  editar(id: number, datos: GuardarServicioDto) {
    return this.http.put<ServicioDto>(this.negocio.url('/servicios/') + id, datos, {
      context: this.negocio.contexto(),
    });
  }
  cambiarEstado(id: number, activo: boolean) {
    return this.http.patch<ServicioDto>(
      this.negocio.url('/servicios/') + id + '/estado',
      { activo },
      { context: this.negocio.contexto() },
    );
  }
}
