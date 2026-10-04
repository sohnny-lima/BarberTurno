// Edge local por CDP, sin dependencias añadidas. Ejecutar desde verificar-reserva-http.ps1.
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
    '--user-data-dir=' + path.join(temporal, 'edge-t26-' + puerto),
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
  if (mensaje.includes('paso 3')) {
    console.log(
      await evaluar(() => ({
        pasoStore: window.ng?.getComponent(document.querySelector('app-reservar'))?.store.paso(),
        pasoStepper: window.ng?.getComponent(document.querySelector('mat-stepper'))?.selectedIndex,
        encabezados: [...document.querySelectorAll('mat-step-header')].map((e) =>
          e.getAttribute('aria-selected'),
        ),
        botonVisible: document.querySelector('[data-confirmar]')?.checkVisibility(),
        botonRect: document.querySelector('[data-confirmar]')?.getBoundingClientRect().toJSON(),
        resumenRect: document.querySelector('.resumen')?.getBoundingClientRect().toJSON(),
      })),
    );
    await captura('t26-fallo-paso3');
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
async function comprobar(ancho, etapa) {
  const estado = await evaluar(() => {
    const raiz = document.documentElement;
    const stepper = document.querySelector('mat-stepper');
    const inputs = [...document.querySelectorAll('app-reservar input')];
    return {
      ancho: raiz.clientWidth,
      contenido: raiz.scrollWidth,
      vertical: stepper.classList.contains('mat-stepper-vertical'),
      sinEtiquetas: inputs.filter(
        (i) =>
          !i.labels?.length && !i.getAttribute('aria-label') && !i.getAttribute('aria-labelledby'),
      ).length,
    };
  });
  assert.equal(
    estado.ancho,
    estado.contenido,
    'Desbordamiento horizontal: ' + JSON.stringify(estado),
  );
  assert.equal(estado.vertical, ancho < 768);
  assert.equal(estado.sinEtiquetas, 0);
  await captura('t26-' + process.env.BT_T26_FASE + '-' + ancho + '-' + etapa);
  console.log(
    `${ancho} px, ${etapa}: ancho=${estado.ancho}/${estado.contenido}; orientación=${estado.vertical ? 'vertical' : 'horizontal'}; campos etiquetados.`,
  );
}
async function ingresar(retorno = false) {
  if (!retorno) await navegar('/ingresar', 'Iniciar sesión');
  await campo('correo', process.env.BT_T26_CLIENTE_CORREO);
  await campo('password', process.env.BT_T26_CLIENTE_PASSWORD);
  await pulsar('Ingresar');
  await hasta(
    () => evaluar(() => location.pathname === '/reservar'),
    'Login no retornó a reservar.',
  );
}
async function seleccionar(ancho, reprogramar = false) {
  await pulsar('Elegir fecha y franja');
  await hasta(() => evaluar(() => !!document.querySelector('mat-chip-option')), 'Faltan franjas.');
  if (!reprogramar) {
    // Interacción con el datepicker real; los límites civiles son los expuestos por la vista.
    await evaluar(() => document.querySelector('mat-datepicker-toggle button').click());
    await hasta(
      () => evaluar(() => !!document.querySelector('mat-datepicker-content')),
      'No apareció el calendario.',
    );
    const hoy = await evaluar(() =>
      document
        .querySelector('.mat-calendar-body-today')
        ?.closest('button')
        ?.getAttribute('aria-label'),
    );
    assert(hoy, 'Falta el día de hoy en el calendario.');
    await captura('t26-calendario-' + ancho);
    const dia = Number(process.env.BT_T26_FECHA.split('-')[2]);
    const diaHoy = await evaluar(() =>
      Number(document.querySelector('.mat-calendar-body-today').textContent),
    );
    if (dia < diaHoy)
      await evaluar(() => document.querySelector('.mat-calendar-next-button').click());
    await evaluar((d) => {
      const boton = [...document.querySelectorAll('button.mat-calendar-body-cell')].find(
        (b) => Number(b.querySelector('.mat-calendar-body-cell-content').textContent) === d,
      );
      if (!boton || boton.getAttribute('aria-disabled') === 'true')
        throw new Error('Fecha no disponible.');
      boton.click();
    }, dia);
    await hasta(
      () =>
        evaluar(
          (f) =>
            document.querySelector('app-reservar').textContent.includes('Fecha consultada: ' + f),
          process.env.BT_T26_FECHA,
        ),
      'El calendario no conserva el día de Lima.',
    );
  }
  await hasta(
    () => evaluar(() => document.querySelectorAll('mat-chip-option').length > 1),
    'Faltan chips tras consultar fecha.',
  );
  await comprobar(ancho, 'franjas');
  await evaluar(() => {
    const chip = document.querySelector('mat-chip-option');
    (chip.querySelector('button') ?? chip).click();
  });
  await hasta(
    () =>
      evaluar(
        () =>
          !!document.querySelector('[data-confirmar]') &&
          !document.querySelector('[data-confirmar]').disabled,
      ),
    'No apareció confirmación habilitada.',
  );
  const resumen = await evaluar(() => document.querySelector('.resumen').textContent);
  assert(
    resumen.includes('30 minutos') && resumen.includes('S/ 25.00') && resumen.includes('2 h antes'),
  );
  assert(
    /09:00\s*[–-]\s*09:30/.test(resumen),
    'El resumen no muestra el intervalo del servidor en Lima.',
  );
  await hasta(
    () =>
      evaluar(
        () =>
          (document.querySelectorAll('mat-step-header')[2]?.getAttribute('aria-selected') ??
            document.querySelectorAll('mat-step-header')[2]?.getAttribute('aria-expanded')) ===
            'true' && document.querySelector('[data-confirmar]')?.checkVisibility(),
      ),
    'El stepper no muestra realmente el paso 3.',
  );
  await evaluar(() => document.querySelector('.resumen').scrollIntoView({ block: 'center' }));
  await comprobar(ancho, 'resumen');
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
      const r = mensaje.params.request;
      if (r.url.includes('/api/disponibilidad') || r.url.includes('/reprogramacion'))
        peticiones.push({ url: r.url, metodo: r.method, cuerpo: r.postData });
    }
  });
  console.log(
    'Navegador local: ' + version.Browser + '; zona Europe/Madrid; fase ' + process.env.BT_T26_FASE,
  );
  const destino = await comando('Target.createTarget', { url: 'about:blank' });
  sesion = (await comando('Target.attachToTarget', { targetId: destino.targetId, flatten: true }))
    .sessionId;
  await comando('Page.enable');
  await comando('Runtime.enable');
  await comando('Network.enable');
  await comando('Emulation.setTimezoneOverride', { timezoneId: 'Europe/Madrid' });
  assert(
    process.env.BT_T26_CLIENTE_CORREO && process.env.BT_T26_CLIENTE_PASSWORD,
    'Faltan credenciales ficticias del proceso.',
  );
  if (process.env.BT_T26_FASE === 'reprogramar') await ingresar();
  for (const ancho of [1440, 360]) {
    await comando('Emulation.setDeviceMetricsOverride', {
      width: ancho,
      height: 900,
      deviceScaleFactor: 1,
      mobile: false,
    });
    const reprogramar = process.env.BT_T26_FASE === 'reprogramar';
    await navegar(
      reprogramar ? '/reservar?reprogramar=' + process.env.BT_T26_RESERVA_ID : '/reservar',
      reprogramar ? 'Reprogramar cita' : 'Reservar un turno',
    );
    await hasta(
      () =>
        evaluar(
          () => !document.querySelector('app-reservar').textContent.includes('Cargando servicios'),
        ),
      'Catálogos pendientes.',
    );
    if (reprogramar) {
      await hasta(
        () =>
          evaluar(() =>
            document.querySelector('.servicio-fijo')?.textContent.includes('30 minutos'),
          ),
        'No se conservó el servicio inactivo.',
      );
    } else {
      await hasta(
        () => evaluar(() => !!document.querySelector('.servicio')),
        'Faltan tarjetas de servicio.',
      );
      await evaluar(() => document.querySelector('.servicio').click());
      assert(
        (await evaluar(() => document.querySelector('mat-select').textContent)).includes(
          'Sin preferencia',
        ),
      );
      await hasta(
        () =>
          evaluar(
            () =>
              !document.querySelector('.servicio').disabled &&
              document.querySelector('.servicio').getAttribute('aria-pressed') === 'true',
          ),
        'Servicio no seleccionado.',
      );
      // Verifica el foco visible por teclado sobre el servicio seleccionado.
      await evaluar(() => document.querySelector('.servicio').focus());
      assert(await evaluar(() => document.activeElement.classList.contains('servicio')));
    }
    await comprobar(ancho, 'servicio');
    await seleccionar(ancho, reprogramar);
    if (!reprogramar && ancho === 360) {
      await pulsar('Confirmar reserva');
      await hasta(
        () =>
          evaluar(
            () =>
              location.pathname === '/ingresar' &&
              new URLSearchParams(location.search).get('returnUrl') === '/reservar',
          ),
        'No se conservó returnUrl.',
      );
      await ingresar(true);
      await hasta(
        () =>
          evaluar(
            () =>
              !!document.querySelector('.resumen') &&
              !document.querySelector('[data-confirmar]').disabled,
          ),
        'No se restauró el paso 3 tras login.',
      );
      await hasta(
        () =>
          evaluar(
            () =>
              (document.querySelectorAll('mat-step-header')[2]?.getAttribute('aria-selected') ??
                document.querySelectorAll('mat-step-header')[2]?.getAttribute('aria-expanded')) ===
                'true' && document.querySelector('[data-confirmar]')?.checkVisibility(),
          ),
        'El paso 3 restaurado no es visible.',
      );
      await evaluar(() => document.querySelector('.resumen').scrollIntoView({ block: 'center' }));
      await comprobar(ancho, 'restauracion');
      console.log(
        'Sin sesión → login → paso 3 restaurado y franja revalidada, sin crear otra reserva.',
      );
    }
    if (reprogramar && ancho === 360) {
      await pulsar('Confirmar reprogramación');
      await hasta(
        () => evaluar(() => location.pathname === '/mis-citas'),
        'No navegó a Mis citas provisional.',
      );
      const post = peticiones.filter(
        (p) => p.metodo === 'POST' && p.url.endsWith('/reprogramacion'),
      );
      assert.equal(post.length, 1, 'La vista envió varias reprogramaciones.');
      assert.equal(JSON.parse(post[0].cuerpo).version, 1);
      assert(
        peticiones.some((p) => p.url.includes('excluirReservaId=' + process.env.BT_T26_RESERVA_ID)),
      );
      console.log(
        'Reprogramación visual: servicio inactivo, referencias 30 min/S/ 25, endpoint con versión, éxito y Mis citas.',
      );
    }
  }
  assert.equal(erroresPagina.length, 0, 'Excepciones JavaScript en la página.');
  console.log('QA visual aprobada; cero excepciones JavaScript.');
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
