// Edge local por CDP, sin dependencias añadidas. Ejecutar desde verificar-usuarios-http.ps1.
// Las credenciales ficticias se reciben solo por variables del proceso y nunca se imprimen.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { mkdir, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const temporal = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../tmp');
await mkdir(temporal, { recursive: true });
const reservaPuerto = createServer();
reservaPuerto.listen(0, '127.0.0.1');
await once(reservaPuerto, 'listening');
const puerto = reservaPuerto.address().port;
await new Promise((resolve) => reservaPuerto.close(resolve));
const edge = spawn(
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  [
    '--headless=new',
    '--no-first-run',
    '--disable-extensions',
    '--disable-background-networking',
    '--disable-sync',
    '--remote-debugging-address=127.0.0.1',
    '--remote-debugging-port=' + puerto,
    '--user-data-dir=' + path.join(temporal, 'edge-t31-' + puerto),
    'about:blank',
  ],
  { windowsHide: true, stdio: 'ignore' },
);
let socket;
let secuencia = 0;
let sesion;
const pendientes = new Map();
const erroresPagina = [];
const esperar = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function hasta(comprobar, mensaje) {
  const limite = Date.now() + 20000;
  while (Date.now() < limite) {
    const dato = await comprobar();
    if (dato) return dato;
    await esperar(100);
  }
  throw new Error(mensaje);
}
function comando(method, params = {}, sessionId = sesion) {
  return new Promise((resolve, reject) => {
    const id = ++secuencia;
    const timeout = setTimeout(() => {
      pendientes.delete(id);
      reject(new Error('CDP no respondió a ' + method));
    }, 15000);
    pendientes.set(id, { resolve, reject, timeout });
    socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
  });
}
async function evaluar(funcion, ...args) {
  const dato = await comando('Runtime.evaluate', {
    expression:
      '(' + funcion.toString() + ')(' + args.map((a) => JSON.stringify(a)).join(',') + ')',
    returnByValue: true,
    awaitPromise: true,
  });
  if (dato.exceptionDetails)
    throw new Error('Falló una comprobación del DOM; expresión y datos omitidos.');
  return dato.result.value;
}
async function navegar(ruta, titulo) {
  await comando('Page.navigate', { url: 'http://localhost:4200' + ruta });
  await hasta(
    () => evaluar((t) => document.querySelector('h1')?.textContent.trim() === t, titulo),
    'La página no presentó el título esperado.',
  );
}
async function pulsar(texto) {
  await evaluar((t) => {
    const botones = [...document.querySelectorAll('button')].filter(
      (b) => b.textContent.trim() === t,
    );
    if (botones.length !== 1 || botones[0].disabled || !botones[0].checkVisibility())
      throw new Error('Botón ambiguo o deshabilitado');
    botones[0].click();
  }, texto);
}
async function campo(nombre, valor) {
  await evaluar(
    (n, v) => {
      const campo = document.querySelector('[formControlName="' + n + '"]');
      campo.value = v;
      campo.dispatchEvent(new Event('input', { bubbles: true }));
      campo.dispatchEvent(new Event('blur', { bubbles: true }));
    },
    nombre,
    valor,
  );
}
async function captura(nombre) {
  await evaluar(async () => {
    await Promise.all(
      document
        .getAnimations()
        .filter((a) => a.effect?.getTiming().iterations !== Infinity)
        .map((a) => a.finished.catch(() => {})),
    );
  });
  const dato = await comando('Page.captureScreenshot', { format: 'png' });
  await writeFile(path.join(temporal, nombre + '.png'), Buffer.from(dato.data, 'base64'));
}
try {
  const version = await hasta(async () => {
    try {
      return await (await fetch('http://127.0.0.1:' + puerto + '/json/version')).json();
    } catch {
      return null;
    }
  }, 'Edge local no quedó disponible.');
  socket = new WebSocket(version.webSocketDebuggerUrl);
  await once(socket, 'open');
  const peticiones = [];
  socket.addEventListener('message', ({ data }) => {
    const mensaje = JSON.parse(data);
    if (mensaje.id) {
      const pendiente = pendientes.get(mensaje.id);
      if (!pendiente) return;
      clearTimeout(pendiente.timeout);
      pendientes.delete(mensaje.id);
      if (mensaje.error) pendiente.reject(new Error('CDP rechazó el comando; datos omitidos.'));
      else pendiente.resolve(mensaje.result);
    } else if (mensaje.method === 'Runtime.exceptionThrown')
      erroresPagina.push('Excepción JavaScript');
    else if (mensaje.method === 'Network.requestWillBeSent') {
      const url = new URL(mensaje.params.request.url);
      if (['/api/reportes/resumen', '/api/reservas'].includes(url.pathname)) peticiones.push(url);
    }
  });
  console.log(
    'Navegador local: ' +
      version.Browser +
      '; zona Europe/Madrid; usuarios y reserva asistida T-31',
  );
  const destino = await comando('Target.createTarget', { url: 'about:blank' });
  sesion = (await comando('Target.attachToTarget', { targetId: destino.targetId, flatten: true }))
    .sessionId;
  await comando('Page.enable');
  await comando('Runtime.enable');
  await comando('Network.enable');
  await comando('Emulation.setTimezoneOverride', { timezoneId: 'Europe/Madrid' });
  await comando('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  });
  assert(
    process.env.BT_ADMIN_CORREO && process.env.BT_ADMIN_PASSWORD && process.env.BT_T31_FECHA,
    'Faltan credenciales ficticias del proceso.',
  );
  await navegar('/ingresar', 'Iniciar sesión');
  await campo('correo', process.env.BT_ADMIN_CORREO);
  await campo('password', process.env.BT_ADMIN_PASSWORD);
  await pulsar('Ingresar');
  await hasta(
    () => evaluar(() => document.querySelector('h1')?.textContent.trim() === 'Agenda y atención'),
    'No se abrió la agenda.',
  );
  await comando(
    'Browser.grantPermissions',
    {
      origin: 'http://localhost:4200',
      permissions: ['clipboardReadWrite', 'clipboardSanitizedWrite'],
    },
    undefined,
  );
  async function escribir(selector, valor) {
    await evaluar(
      (sel, v) => {
        const el = document.querySelector(sel);
        if (!el || !el.checkVisibility()) throw new Error('Campo no visible');
        el.focus();
        el.value = v;
        el.dispatchEvent(new Event('input', { bubbles: true }));
      },
      selector,
      valor,
    );
  }
  async function visiblePaso(texto) {
    return evaluar(
      (t) =>
        [...document.querySelectorAll('[role=tabpanel], .mat-vertical-content')].some(
          (p) => p.checkVisibility() && p.textContent.includes(t),
        ),
      texto,
    );
  }
  for (const ancho of [1440, 360]) {
    await comando('Emulation.setDeviceMetricsOverride', {
      width: ancho,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await navegar('/admin/usuarios', 'Usuarios');
    await hasta(
      () => evaluar(() => document.querySelectorAll('app-usuarios tbody tr').length >= 3),
      'Faltan usuarios',
    );
    await escribir('app-usuarios input', 'Cliente ficticio T31');
    await pulsar('Buscar');
    await hasta(
      () =>
        evaluar(
          () =>
            document.querySelectorAll('app-usuarios tbody tr').length === 1 &&
            document
              .querySelector('app-usuarios tbody')
              .textContent.includes('Cliente ficticio T31'),
        ),
      'Búsqueda de usuario falló',
    );
    assert(
      await evaluar(() => document.documentElement.scrollWidth <= innerWidth),
      'Desbordamiento global usuarios',
    );
    if (ancho === 360)
      assert(
        await evaluar(() => {
          const tabla = document.querySelector('app-usuarios .tabla');
          return (
            tabla.scrollWidth > tabla.clientWidth &&
            getComputedStyle(tabla).overflowX === 'auto' &&
            tabla.tabIndex === 0
          );
        }),
        'Tabla móvil sin desplazamiento controlado',
      );
    await captura('t31-' + ancho + '-usuarios');
    if (ancho === 1440) {
      await pulsar('Restablecer contraseña');
      await hasta(
        () => evaluar(() => !!document.querySelector('app-confirmar-estado-dialogo')),
        'Falta confirmación',
      );
      await evaluar(() => {
        const b = [...document.querySelectorAll('app-confirmar-estado-dialogo button')].find(
          (b) => b.textContent.trim() === 'Restablecer contraseña',
        );
        if (!b) throw new Error('Falta confirmar');
        b.click();
      });
      await hasta(
        () => evaluar(() => !!document.querySelector('app-password-temporal-dialogo')),
        'No apareció temporal',
      );
      assert(
        await evaluar(() => {
          const t = document
            .querySelector('app-password-temporal-dialogo .password')
            .textContent.trim();
          return t.length === 12 && /[A-Za-z]/.test(t) && /[0-9]/.test(t);
        }),
        'Temporal incorrecta; valor omitido',
      );
      await comando('Input.dispatchKeyEvent', {
        type: 'keyDown',
        key: 'Escape',
        code: 'Escape',
        windowsVirtualKeyCode: 27,
      });
      await comando('Input.dispatchKeyEvent', {
        type: 'keyUp',
        key: 'Escape',
        code: 'Escape',
        windowsVirtualKeyCode: 27,
      });
      await evaluar(() => document.querySelector('.cdk-overlay-backdrop').click());
      assert(
        await evaluar(() => !!document.querySelector('app-password-temporal-dialogo')),
        'Cierre accidental de temporal',
      );
      await pulsar('Copiar');
      await hasta(
        () =>
          evaluar(() =>
            document
              .querySelector('app-password-temporal-dialogo')
              .textContent.includes('Contraseña copiada.'),
          ),
        'Copiar no confirmó',
      );
      // No se captura ni escribe la contraseña temporal en ningún artefacto.
      await pulsar('Cerrar');
      await hasta(
        () => evaluar(() => !document.querySelector('app-password-temporal-dialogo')),
        'No cerró el diálogo',
      );
      await pulsar('Desactivar');
      await hasta(
        () => evaluar(() => !!document.querySelector('app-confirmar-estado-dialogo')),
        'Falta confirmar estado',
      );
      await evaluar(() => {
        const b = [...document.querySelectorAll('app-confirmar-estado-dialogo button')].find(
          (b) => b.textContent.trim() === 'Desactivar',
        );
        if (!b) throw new Error('Falta confirmar');
        b.click();
      });
      await hasta(
        () =>
          evaluar(() =>
            document.querySelector('app-usuarios tbody').textContent.includes('Inactivo'),
          ),
        'No se desactivó',
      );
      await pulsar('Activar');
      await hasta(
        () => evaluar(() => !!document.querySelector('app-confirmar-estado-dialogo')),
        'Falta confirmar estado',
      );
      await evaluar(() => {
        const b = [...document.querySelectorAll('app-confirmar-estado-dialogo button')].find(
          (b) => b.textContent.trim() === 'Activar',
        );
        if (!b) throw new Error('Falta confirmar');
        b.click();
      });
      await hasta(
        () =>
          evaluar(
            () =>
              !document.querySelector('app-confirmar-estado-dialogo') &&
              document.querySelector('app-usuarios tbody').textContent.includes('Activo'),
          ),
        'No se reactivó',
      );
    }
    await navegar('/reservar', 'Reservar un turno');
    await hasta(
      () => evaluar(() => !!document.querySelector('app-selector-cliente input')),
      'Falta paso Cliente',
    );
    assert(await visiblePaso('Buscar cliente'), 'Paso Cliente invisible');
    assert(
      await evaluar(
        (a) =>
          document
            .querySelector('mat-stepper')
            .classList.contains(a < 768 ? 'mat-stepper-vertical' : 'mat-stepper-horizontal'),
        ancho,
      ),
      'Orientación del stepper incorrecta',
    );
    await escribir('app-selector-cliente input', 'Cliente ficticio T31');
    await hasta(
      () =>
        evaluar(() =>
          [...document.querySelectorAll('mat-option')].some((o) =>
            o.textContent.includes('Cliente ficticio T31'),
          ),
        ),
      'Autocompletado sin opciones',
    );
    await evaluar(() => {
      const opciones = [...document.querySelectorAll('mat-option')].filter((o) =>
        o.textContent.includes('Cliente ficticio T31'),
      );
      if (opciones.length !== 1) throw new Error('Cliente ambiguo');
      opciones[0].click();
    });
    await pulsar('Elegir servicio');
    await hasta(
      () =>
        evaluar(() => [...document.querySelectorAll('.servicio')].some((b) => b.checkVisibility())),
      'Paso servicio invisible',
    );
    await evaluar(() => {
      const b = [...document.querySelectorAll('.servicio')].find((b) =>
        b.textContent.includes('Corte T31'),
      );
      if (!b || !b.checkVisibility()) throw new Error('Falta servicio propio');
      b.click();
    });
    await pulsar('Elegir fecha y franja');
    await hasta(() => visiblePaso('Fecha de la cita'), 'Paso fecha invisible');
    await evaluar(() => document.querySelector('mat-datepicker-toggle button').click());
    await hasta(
      () => evaluar(() => !!document.querySelector('mat-calendar')),
      'No abrió calendario',
    );
    await evaluar((fecha) => {
      const [a, m, d] = fecha.split('-').map(Number);
      const meses = [
        'enero',
        'febrero',
        'marzo',
        'abril',
        'mayo',
        'junio',
        'julio',
        'agosto',
        'septiembre',
        'octubre',
        'noviembre',
        'diciembre',
      ];
      const etiqueta = d + ' de ' + meses[m - 1] + ' de ' + a;
      const b = [...document.querySelectorAll('mat-calendar button')].find(
        (b) => b.getAttribute('aria-label')?.toLowerCase() === etiqueta,
      );
      if (!b || b.disabled) throw new Error('Fecha no disponible');
      b.click();
    }, process.env.BT_T31_FECHA);
    await hasta(
      () =>
        evaluar(
          (f) =>
            document.querySelector('app-reservar').textContent.includes('Fecha consultada: ' + f) &&
            !document.querySelector('app-reservar').textContent.includes('Consultando franjas') &&
            document.querySelectorAll('mat-chip-option').length > 0,
          process.env.BT_T31_FECHA,
        ),
      'Faltan franjas de la fecha elegida',
    );
    await evaluar(() => {
      const b = [...document.querySelectorAll('mat-chip-option')].find((b) => b.checkVisibility());
      if (!b) throw new Error('Franja invisible');
      (b.querySelector('button') ?? b).click();
    });
    await hasta(
      () => evaluar(() => document.querySelector('.resumen')?.checkVisibility()),
      'Resumen invisible',
    );
    assert(
      await evaluar(() =>
        document.querySelector('.resumen').textContent.includes('Cliente ficticio T31'),
      ),
      'Resumen sin cliente',
    );
    assert(
      await evaluar(
        () =>
          document
            .querySelector('.resumen')
            .textContent.includes('Puede gestionar esta cita desde Agenda.') &&
          !document.querySelector('.resumen').textContent.includes('hasta 2 h antes'),
      ),
      'Resumen ADMIN conserva el aviso de CLIENTE',
    );
    assert(
      await evaluar(() => document.documentElement.scrollWidth <= innerWidth),
      'Desbordamiento global reserva',
    );
    await captura('t31-' + ancho + '-asistida');
    await pulsar('Confirmar reserva');
    await hasta(
      () => evaluar(() => document.querySelector('h1')?.textContent.trim() === 'Agenda y atención'),
      'Reserva asistida no concluyó',
    );
  }
  assert.equal(erroresPagina.length, 0, 'Excepciones JavaScript');
  console.log(
    'QA T-31 aprobada: usuarios y reserva asistida a 1440/360 px, Cliente previo, resumen y creación; temporal una vez, Esc/clic fuera protegidos, Copiar y estados; cero excepciones.',
  );
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try {
      await comando('Browser.close', {}, undefined);
    } catch {
      /* El cierre puede preceder a la respuesta. */
    }
    socket.close();
  }
  for (const pendiente of pendientes.values()) clearTimeout(pendiente.timeout);
  if (edge.exitCode === null) {
    await Promise.race([once(edge, 'exit'), esperar(3000)]);
    if (edge.exitCode === null)
      spawnSync('taskkill.exe', ['/PID', String(edge.pid), '/T', '/F'], {
        windowsHide: true,
        stdio: 'ignore',
      });
  }
}
