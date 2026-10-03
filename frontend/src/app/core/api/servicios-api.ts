import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { GuardarServicioDto, ServicioDto } from '../modelos/catalogo';

@Injectable({ providedIn: 'root' })
export class ServiciosApi {
  private readonly http = inject(HttpClient);
  listar(incluirInactivos = false) {
    return this.http.get<ServicioDto[]>('/api/servicios', { params: { incluirInactivos } });
  }
  crear(datos: GuardarServicioDto) {
    return this.http.post<ServicioDto>('/api/servicios', datos);
  }
  editar(id: number, datos: GuardarServicioDto) {
    return this.http.put<ServicioDto>('/api/servicios/' + id, datos);
  }
  cambiarEstado(id: number, activo: boolean) {
    return this.http.patch<ServicioDto>('/api/servicios/' + id + '/estado', { activo });
  }
}
