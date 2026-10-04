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
    '--user-data-dir=' + path.join(temporal, 'edge-t27-' + puerto),
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
      const r = mensaje.params.request;
      if (r.url.includes('/cancelacion'))
        peticiones.push({ url: r.url, metodo: r.method, cuerpo: r.postData });
    }
  });
  console.log('Navegador local: ' + version.Browser + '; zona Europe/Madrid; Mis citas');
  const destino = await comando('Target.createTarget', { url: 'about:blank' });
  sesion = (await comando('Target.attachToTarget', { targetId: destino.targetId, flatten: true }))
    .sessionId;
  await comando('Page.enable');
  await comando('Runtime.enable');
  await comando('Network.enable');
  await comando('Emulation.setTimezoneOverride', { timezoneId: 'Europe/Madrid' });
  assert(
    process.env.BT_T27_CLIENTE_CORREO && process.env.BT_T27_CLIENTE_PASSWORD,
    'Faltan credenciales ficticias del proceso.',
  );
  await comando('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  });
  await navegar('/ingresar', 'Iniciar sesión');
  await campo('correo', process.env.BT_T27_CLIENTE_CORREO);
  await campo('password', process.env.BT_T27_CLIENTE_PASSWORD);
  await pulsar('Ingresar');
  await hasta(() => evaluar(() => location.pathname === '/reservar'), 'El login no terminó.');
  await navegar('/mis-citas', 'Mis citas');
  const contador = (valor) =>
    hasta(
      () =>
        evaluar(
          (v) =>
            document.querySelector('[data-contador-avisos]')?.textContent.trim() ===
            'Avisos sin leer: ' + v,
          valor,
        ),
      'El contador no muestra ' + valor,
    );
  const codigo = 'BT-' + process.env.BT_T27_RESERVA_ID;
  await hasta(
    () =>
      evaluar(
        (c) => document.querySelector('app-reserva-tarjeta')?.textContent.includes(c),
        codigo,
      ),
    'Falta la cita propia.',
  );
  await contador(1);
  assert(
    (await evaluar(() => document.querySelector('app-reserva-tarjeta').textContent)).includes(
      '09:00',
    ),
  );
  assert(
    (await evaluar(() => document.querySelector('app-reserva-tarjeta').textContent)).includes(
      '09:30',
    ),
  );
  await captura('t27-1440-proximas');
  await pulsar('Marcar como leído');
  await contador(0);
  await pulsar('Cancelar');
  await hasta(
    () => evaluar(() => !!document.querySelector('app-cancelar-dialogo')),
    'Falta el diálogo.',
  );
  await campo('motivo', 'Cancelación ficticia del recorrido T-27');
  await captura('t27-1440-dialogo');
  await pulsar('Cancelar cita');
  await hasta(
    () => evaluar(() => !document.querySelector('app-cancelar-dialogo')),
    'El diálogo no cerró.',
  );
  await contador(1);
  await hasta(
    () =>
      evaluar(() =>
        document.querySelector('app-reserva-tarjeta')?.textContent.includes('Cancelada'),
      ),
    'No se recargó la tarjeta.',
  );
  await hasta(
    () => evaluar(() => document.querySelectorAll('app-avisos-panel li').length === 2),
    'No se recargaron los avisos.',
  );
  const post = peticiones.filter((p) => p.metodo === 'POST');
  assert.equal(post.length, 1);
  assert.equal(JSON.parse(post[0].cuerpo).version, 0);
  await pulsar('Marcar todos como leídos');
  await contador(0);
  await comando('Emulation.setDeviceMetricsOverride', {
    width: 360,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  });
  await hasta(() => evaluar(() => window.innerWidth === 360), 'Falta vista móvil.');
  await captura('t27-360-cancelada');
  assert(
    await evaluar(
      () => document.documentElement.scrollWidth === document.documentElement.clientWidth,
    ),
    'Desbordamiento horizontal móvil.',
  );
  assert.equal(
    await evaluar(
      () =>
        [...document.querySelectorAll('app-mis-citas input')].filter(
          (i) => !i.labels?.length && !i.getAttribute('aria-labelledby'),
        ).length,
    ),
    0,
  );
  await evaluar(() =>
    [...document.querySelectorAll('[role=tab]')]
      .find((t) => t.textContent.trim() === 'Historial')
      .click(),
  );
  await hasta(
    () =>
      evaluar(() =>
        document
          .querySelector('app-mis-citas')
          .textContent.includes('No hay citas con estos filtros.'),
      ),
    'Historial no quedó vacío.',
  );
  await captura('t27-360-historial');
  await campo('desde', '2026-10-08');
  await campo('hasta', '2026-10-07');
  await pulsar('Filtrar');
  await hasta(
    () =>
      evaluar(() =>
        document
          .querySelector('app-mis-citas')
          .textContent.includes('La fecha Desde no puede ser posterior a Hasta.'),
      ),
    'No se mostró el error de filtro.',
  );
  await captura('t27-360-filtro-invalido');
  console.log(
    'Mis citas: tarjeta, Lima, cancelación con versión=0, avisos recientes, contador 1->0->1->0, historial, filtro inválido y 1440/360 px aprobados.',
  );
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
