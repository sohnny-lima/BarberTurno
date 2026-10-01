import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-privacidad',
  imports: [RouterLink],
  template: `<section class="pagina-identidad tarjeta">
    <h1>Aviso de privacidad</h1>
    <p>
      BarberTurno es un proyecto académico de una barbería ficticia de Huamanga. Este entorno
      utiliza únicamente datos de demostración. No ingrese datos personales reales.
    </p>
    <p>
      El registro solicita nombre, correo, teléfono y contraseña para identificar la cuenta,
      gestionar sus citas y permitir el acceso. El consentimiento se registra al crear la cuenta. No
      se ofrecen comunicaciones comerciales ni se envían mensajes a servicios externos.
    </p>
    <p>
      Este aviso de ejemplo toma como referencia la Ley 29733 de protección de datos personales del
      Perú. La información del responsable, los plazos de conservación y el canal para ejercer los
      derechos de acceso, rectificación, cancelación y oposición deberán completarse y validarse
      antes de un uso real.
    </p>
    <a routerLink="/registro">Volver al registro</a>
  </section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Privacidad {}
