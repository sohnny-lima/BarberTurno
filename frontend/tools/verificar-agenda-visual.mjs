// Edge local por CDP, sin dependencias añadidas. Ejecutar desde verificar-agenda-http.ps1.
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
    '--user-data-dir=' + path.join(temporal, 'edge-t28-' + puerto),
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
  console.log('Navegador local: ' + version.Browser + '; zona Europe/Madrid; Agenda');
  const destino = await comando('Target.createTarget', { url: 'about:blank' });
  sesion = (await comando('Target.attachToTarget', { targetId: destino.targetId, flatten: true }))
    .sessionId;
  await comando('Page.enable');
  await comando('Runtime.enable');
  await comando('Network.enable');
  await comando('Emulation.setTimezoneOverride', { timezoneId: 'Europe/Madrid' });
  assert(
    process.env.BT_ADMIN_CORREO &&
      process.env.BT_ADMIN_PASSWORD &&
      process.env.BT_T28_BARBERO_CORREO &&
      process.env.BT_T28_BARBERO_PASSWORD,
    'Faltan credenciales ficticias del proceso.',
  );
  async function login(correo, password) {
    await navegar('/ingresar', 'Iniciar sesión');
    await campo('correo', correo);
    await campo('password', password);
    await pulsar('Ingresar');
    await hasta(
      () => evaluar(() => document.querySelector('h1')?.textContent.trim() === 'Agenda y atención'),
      'No se abrió la agenda.',
    );
    await campo('fecha', process.env.BT_T28_FECHA);
    await pulsar('Actualizar agenda');
    await hasta(
      () => evaluar(() => document.querySelectorAll('app-agenda article').length === 1),
      'No se mostró la cita ficticia.',
    );
  }
  async function movil() {
    await comando('Emulation.setDeviceMetricsOverride', {
      width: 360,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });
    assert(
      await evaluar(
        () => document.documentElement.scrollWidth === document.documentElement.clientWidth,
      ),
      'Desbordamiento horizontal móvil.',
    );
  }
  await comando('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  });
  await login(process.env.BT_ADMIN_CORREO, process.env.BT_ADMIN_PASSWORD);
  assert(
    await evaluar(() =>
      document.querySelector('[data-selector-barbero]')?.textContent.includes('Todos'),
    ),
    'ADMIN sin selector Todos.',
  );
  if (process.env.BT_T28_FASE === 'futura') {
    const fila = await evaluar(() => document.querySelector('app-agenda article').textContent);
    for (const texto of [
      'Cliente ficticio T28',
      '999000027',
      'Profesional ficticio T28',
      'Confirmada',
      '09:00',
      '09:30',
    ])
      assert(fila.includes(texto), 'Falta campo en la fila: ' + texto);
    await captura('t28-1440-agenda-admin');
    await pulsar('Cancelar');
    await hasta(
      () => evaluar(() => !!document.querySelector('app-cancelar-dialogo')),
      'Falta diálogo cancelación.',
    );
    assert(
      await evaluar(() => document.querySelector('app-cancelar-dialogo [type=submit]').disabled),
      'Cancelación ADMIN aceptó motivo vacío.',
    );
    await campo('motivo', 'abc');
    assert(
      await evaluar(() => document.querySelector('app-cancelar-dialogo [type=submit]').disabled),
      'Cancelación ADMIN aceptó motivo corto.',
    );
    await campo('motivo', 'Cambio ficticio T28');
    assert(
      await evaluar(() => !document.querySelector('app-cancelar-dialogo [type=submit]').disabled),
      'Motivo válido no habilita cancelación.',
    );
    await captura('t28-1440-cancelacion');
    await pulsar('Conservar cita');
    await hasta(
      () => evaluar(() => !document.querySelector('app-cancelar-dialogo')),
      'Cancelación no cerró.',
    );
    await pulsar('Reprogramar');
    await hasta(
      () => evaluar(() => !!document.querySelector('app-reprogramar-dialogo')),
      'Falta reprogramación.',
    );
    await hasta(
      () =>
        evaluar(
          () =>
            !document
              .querySelector('app-reprogramar-dialogo')
              .textContent.includes('Consultando franjas'),
        ),
      'Franjas pendientes.',
    );
    await evaluar(() =>
      document.querySelector('app-reprogramar-dialogo [formControlName=inicio]').click(),
    );
    await hasta(
      () => evaluar(() => document.querySelectorAll('mat-option').length > 0),
      'Sin franjas del servidor.',
    );
    await evaluar(() => document.querySelector('mat-option').click());
    assert(
      await evaluar(() => document.querySelector('app-reprogramar-dialogo [type=submit]').disabled),
      'Reprogramación ADMIN aceptó motivo vacío.',
    );
    await campo('motivo', 'abc');
    assert(
      await evaluar(() => document.querySelector('app-reprogramar-dialogo [type=submit]').disabled),
      'Reprogramación ADMIN aceptó motivo corto.',
    );
    await campo('motivo', 'Cambio ficticio T28');
    assert(
      await evaluar(
        () => !document.querySelector('app-reprogramar-dialogo [type=submit]').disabled,
      ),
      'Motivo válido no habilita reprogramación.',
    );
    await captura('t28-1440-reprogramacion');
    await pulsar('Conservar cita');
    await hasta(
      () => evaluar(() => !document.querySelector('app-reprogramar-dialogo')),
      'Reprogramación no cerró.',
    );
    await movil();
    await captura('t28-360-agenda-admin');
    await pulsar('Salir');
    await login(process.env.BT_T28_BARBERO_CORREO, process.env.BT_T28_BARBERO_PASSWORD);
    assert(
      await evaluar(() => !document.querySelector('[data-selector-barbero]')),
      'BARBERO con selector.',
    );
    assert(
      await evaluar(() => {
        const botones = [...document.querySelectorAll('app-agenda article button')].map((b) =>
          b.textContent.trim(),
        );
        return (
          !botones.includes('Cancelar') &&
          !botones.includes('Reprogramar') &&
          botones.includes('Ver cambios')
        );
      }),
      'Acciones indebidas de BARBERO.',
    );
    await captura('t28-360-agenda-barbero');
    console.log(
      'Agenda futura: campos Lima, ADMIN Todos, motivos obligatorios en ambos diálogos, BARBERO sin selector/acciones administrativas; 1440/360 aprobados.',
    );
  } else {
    assert(
      await evaluar(() =>
        document.querySelector('app-agenda article').textContent.includes('Completada'),
      ),
      'No se ve la atención completada.',
    );
    await pulsar('Ver cambios');
    await hasta(
      () => evaluar(() => document.querySelectorAll('app-auditoria-dialogo article').length === 3),
      'No se cargaron tres cambios.',
    );
    const texto = await evaluar(() => document.querySelector('app-auditoria-dialogo').textContent);
    for (const valor of [
      'Crear',
      'Iniciar atención',
      'Completar',
      'Profesional ficticio T28',
      'Confirmada → En atención',
      'En atención → Completada',
      'Sin motivo',
    ])
      assert(texto.includes(valor), 'Falta dato auditado: ' + valor);
    assert(
      await evaluar(() =>
        [...document.querySelectorAll('app-auditoria-dialogo article')]
          .slice(1)
          .every((a) => !a.querySelector('.valores').textContent.includes('Barbero #')),
      ),
      'Auditoría inventa profesional en instantáneas de transición.',
    );
    await captura('t28-1440-auditoria');
    await movil();
    await captura('t28-360-auditoria');
    console.log(
      'Auditoría real ADMIN: CREAR/INICIAR/COMPLETAR, actores, anteriores/nuevos y horario Lima, 1440/360 aprobados.',
    );
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
