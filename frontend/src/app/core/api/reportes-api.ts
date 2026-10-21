import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { inject, Injectable } from '@angular/core';
import { ConsultaResumen, ResumenReporteDto } from '../modelos/reportes';

@Injectable({ providedIn: 'root' })
export class ReportesApi {
  private readonly http = inject(HttpClient);

  resumen(consulta: ConsultaResumen) {
    return this.http.get<ResumenReporteDto>('/api/reportes/resumen', {
      params: this.parametros(consulta),
    });
  }

  excel(consulta: ConsultaResumen) {
    return this.http
      .get('/api/reportes/resumen.xlsx', {
        params: this.parametros(consulta),
        headers: { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
        responseType: 'blob',
        observe: 'response',
      })
      .pipe(
        catchError((error: HttpErrorResponse) => {
          if (!(error.error instanceof Blob)) return throwError(() => error);
          // HttpClient conserva responseType=blob también para application/problem+json.
          return new Observable<never>((suscriptor) => {
            const lector = new FileReader();
            lector.onload = () => {
              let problema: unknown = error.error;
              try {
                problema = JSON.parse(String(lector.result));
              } catch {
                // Un proxy puede devolver HTML; el patrón existente usa el error genérico.
              }
              suscriptor.error(
                new HttpErrorResponse({
                  error: problema,
                  headers: error.headers,
                  status: error.status,
                  statusText: error.statusText,
                  url: error.url ?? undefined,
                }),
              );
            };
            lector.onerror = () => suscriptor.error(error);
            lector.readAsText(error.error);
            return () => {
              if (lector.readyState === FileReader.LOADING) lector.abort();
            };
          });
        }),
      );
  }

  private parametros(consulta: ConsultaResumen) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(consulta)) {
      if (valor !== undefined) params = params.set(clave, valor);
    }
    return params;
  }
}
