import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ConversacionBandejaDto,
  DetalleConversacionDto,
  MensajeBandejaDto,
} from '../modelos/bandeja-whatsapp';
import { Pagina } from '../modelos/pagina';
import { NegocioActual } from '../negocio/negocio-actual';

/**
 * Cliente de la API de la bandeja de WhatsApp para el personal de gestión (V2-11).
 * Rutas bajo `/api/n/{slug}/whatsapp/conversaciones`.
 */
@Injectable({ providedIn: 'root' })
export class BandejaWhatsAppApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();

  /**
   * Lista conversaciones derivadas en modo humano ordenadas por última entrada descendente.
   * @param pagina número de página (0-indexado)
   * @param tamano tamaño de la página (≤ 50)
   */
  listar(pagina = 0, tamano = 20): Observable<Pagina<ConversacionBandejaDto>> {
    return this.http.get<Pagina<ConversacionBandejaDto>>(
      this.negocio.url('/whatsapp/conversaciones'),
      {
        context: this.negocio.contexto(),
        params: { pagina, tamano },
      },
    );
  }

  /**
   * Obtiene el detalle de una conversación derivada con su historial de mensajes.
   * @param id identificador de la conversación
   * @param antesDe identificador de mensaje para paginar hacia atrás (opcional)
   */
  detalle(id: number, antesDe?: number): Observable<DetalleConversacionDto> {
    return this.http.get<DetalleConversacionDto>(
      this.negocio.url('/whatsapp/conversaciones/' + id),
      {
        context: this.negocio.contexto(),
        ...(antesDe != null ? { params: { antesDe } } : {}),
      },
    );
  }

  /**
   * Envía una respuesta libre del personal a la conversación en modo humano.
   * @param id identificador de la conversación
   * @param texto contenido del mensaje (1 a 4096 caracteres)
   */
  responder(id: number, texto: string): Observable<MensajeBandejaDto> {
    return this.http.post<MensajeBandejaDto>(
      this.negocio.url('/whatsapp/conversaciones/' + id + '/respuestas'),
      { texto },
      { context: this.negocio.contexto() },
    );
  }

  /**
   * Devuelve la conversación al asistente virtual.
   * @param id identificador de la conversación
   */
  devolver(id: number): Observable<void> {
    return this.http.post<void>(
      this.negocio.url('/whatsapp/conversaciones/' + id + '/devolver'),
      null,
      { context: this.negocio.contexto() },
    );
  }
}
