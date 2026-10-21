import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { inject, Injectable } from '@angular/core';
import { ConsultaResumen, ResumenReporteDto } from '../modelos/reportes';

@Injectable({ providedIn: 'root' })
export class ReportesApi {
  private readonly http = inject(HttpClient);

  resumen(consulta: ConsultaResumen) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta)) {
      if (valor !== undefined) params = params.set(clave, valor);
    }
    return this.http.get<ResumenReporteDto>('/api/reportes/resumen', { params });
  }

  excel(_consulta: ConsultaResumen) {
    return of(new HttpResponse<Blob>({ body: new Blob() }));
  }
}
