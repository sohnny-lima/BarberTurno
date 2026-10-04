import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Pagina } from '../modelos/pagina';
import { ConsultaUsuarios, UsuarioAdminDto } from '../modelos/usuarios';
@Injectable({ providedIn: 'root' })
export class UsuariosApi {
  private readonly http = inject(HttpClient);
  listar(consulta: ConsultaUsuarios) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta))
      if (valor !== undefined) params = params.set(clave, valor);
    return this.http.get<Pagina<UsuarioAdminDto>>('/api/usuarios', { params });
  }
  restablecer(id: number) {
    return this.http.post<{ passwordTemporal: string }>(
      '/api/usuarios/' + id + '/restablecer-password',
      {},
    );
  }
  cambiarEstado(id: number, activo: boolean) {
    return this.http.patch<void>('/api/usuarios/' + id + '/estado', { activo });
  }
}
