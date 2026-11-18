import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-privacidad',
  imports: [RouterLink, MatButtonModule],
  template: `<section class="lectura">
    <h1>Aviso de privacidad</h1>
    <p>
      Responsable del tratamiento: Sohnny Lima. Contacto para consultas y solicitudes sobre sus
      datos: <a href="mailto:limasoin.sli@gmail.com">limasoin.sli@gmail.com</a>.
    </p>
    <p>
      Al crear su cuenta, BarberTurno trata su nombre, correo, teléfono y contraseña (la contraseña
      se guarda de forma irreversible). Al reservar, registra la barbería, el servicio, el
      profesional, la fecha y el estado de sus citas.
    </p>
    <p>
      Los datos se usan para identificar su cuenta, gestionar sus reservas y permitir que la
      barbería elegida atienda sus citas. Solo acceden a ellos la barbería donde reserva y la
      administración de la plataforma. No se usan con fines publicitarios ni se venden.
    </p>
    <p>
      Los datos se alojan en Microsoft Azure (Estados Unidos). La aplicación usa solo una cookie
      técnica para mantener la sesión iniciada.
    </p>
    <p>
      Se conservan mientras la cuenta esté activa o hasta que solicite su cancelación. Puede ejercer
      sus derechos de acceso, rectificación, cancelación y oposición (Ley 29733 de Protección de
      Datos Personales del Perú) escribiendo al correo de contacto. El consentimiento se registra al
      crear la cuenta.
    </p>
    <a mat-button routerLink="/registro">Volver al registro</a>
  </section>`,
  styleUrl: './privacidad.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Privacidad {}
