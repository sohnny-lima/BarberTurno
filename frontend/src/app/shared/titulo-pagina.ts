import { Injectable, signal } from '@angular/core';

/** Título de la vista activa para la barra móvil; la página conserva su único h1. */
@Injectable({ providedIn: 'root' })
export class TituloPagina {
  readonly texto = signal<string | null>(null);
  readonly actualizarAgenda = signal<{
    ejecutar: () => void;
    desactivada: () => boolean;
  } | null>(null);
}
