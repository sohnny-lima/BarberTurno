import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  BarberoDto,
  CambiarEstadoBarberoDto,
  CrearBarberoDto,
  EditarBarberoDto,
} from '../modelos/catalogo';

@Injectable({ providedIn: 'root' })
export class BarberosApi {
  private readonly http = inject(HttpClient);
  listar(incluirInactivos = false) {
    return this.http.get<BarberoDto[]>('/api/barberos', { params: { incluirInactivos } });
  }
  crear(datos: CrearBarberoDto) {
    return this.http.post<BarberoDto>('/api/barberos', datos);
  }
  editar(id: number, datos: EditarBarberoDto) {
    return this.http.put<BarberoDto>('/api/barberos/' + id, datos);
  }
  cambiarEstado(id: number, activo: boolean) {
    return this.http.patch<CambiarEstadoBarberoDto>('/api/barberos/' + id + '/estado', { activo });
  }
}
