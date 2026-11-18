import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, BaseRouteReuseStrategy } from '@angular/router';
/** Destruye componentes, providers y formularios incluso si Angular reutilizaría la ruta parametrizada. */
@Injectable()
export class ReutilizacionNegocio extends BaseRouteReuseStrategy {
  override shouldReuseRoute(futura: ActivatedRouteSnapshot, actual: ActivatedRouteSnapshot) {
    const slug = (ruta: ActivatedRouteSnapshot) =>
      ruta.pathFromRoot.map((nodo) => nodo.paramMap.get('slug')).find(Boolean);
    return (
      super.shouldReuseRoute(futura, actual) &&
      futura.paramMap.get('token') === actual.paramMap.get('token') &&
      ((!slug(futura) && !slug(actual)) ||
        (slug(futura) === slug(actual) &&
          futura.queryParamMap.get('papel') === actual.queryParamMap.get('papel')))
    );
  }
}
