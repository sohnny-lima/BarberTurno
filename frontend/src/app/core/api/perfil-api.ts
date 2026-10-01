import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ActualizarPerfilDto, PerfilDto } from '../modelos/identidad';

@Injectable({ providedIn: 'root' })
export class PerfilApi {
  private readonly http = inject(HttpClient);
  obtener() {
    return this.http.get<PerfilDto>('/api/perfil');
  }
  actualizar(datos: ActualizarPerfilDto) {
    return this.http.put<PerfilDto>('/api/perfil', datos);
  }
}
