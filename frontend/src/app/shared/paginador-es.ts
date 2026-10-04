import { MatPaginatorIntl } from '@angular/material/paginator';

export function paginadorEspanol(): MatPaginatorIntl {
  const etiquetas = new MatPaginatorIntl();
  etiquetas.itemsPerPageLabel = 'Elementos por página:';
  etiquetas.nextPageLabel = 'Página siguiente';
  etiquetas.previousPageLabel = 'Página anterior';
  etiquetas.firstPageLabel = 'Primera página';
  etiquetas.lastPageLabel = 'Última página';
  etiquetas.getRangeLabel = (pagina, tamano, total) =>
    total === 0
      ? '0 de 0'
      : pagina * tamano + 1 + '–' + Math.min((pagina + 1) * tamano, total) + ' de ' + total;
  return etiquetas;
}
