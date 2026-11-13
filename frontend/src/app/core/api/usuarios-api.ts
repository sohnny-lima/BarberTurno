import { NegocioActual } from '../negocio/negocio-actual';
import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Pagina } from '../modelos/pagina';
import { ConsultaUsuarios, UsuarioAdminDto } from '../modelos/usuarios';
@Injectable({ providedIn: 'root' })
export class UsuariosApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  listar(consulta: ConsultaUsuarios) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta))
      if (valor !== undefined) params = params.set(clave, valor);
    return this.http.get<Pagina<UsuarioAdminDto>>(this.negocio.url('/usuarios'), {
      params,
      context: this.negocio.contexto(),
    });
  }
  restablecer(id: number) {
    return this.http.post<{ passwordTemporal: string }>(
      this.negocio.url('/usuarios/') + id + '/restablecer-password',
      {},
      { context: this.negocio.contexto() },
    );
  }
  cambiarEstado(id: number, activo: boolean) {
    return this.http.patch<void>(
      this.negocio.url('/usuarios/') + id + '/estado',
      { activo },
      { context: this.negocio.contexto() },
    );
  }
}
