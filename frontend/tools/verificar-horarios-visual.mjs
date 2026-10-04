// Edge local por CDP, sin dependencias añadidas. Ejecutar desde verificar-horarios-http.ps1.
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
    '--user-data-dir=' + path.join(temporal, 'edge-t18-' + puerto),
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
    if (botones.length !== 1 || botones[0].disabled)
      throw new Error('Botón ambiguo o deshabilitado');
    botones[0].click();
  }, texto);
}
async function campo(nombre, valor) {
  await evaluar(
    (n, v) => {
      const campo =
        document.querySelectorAll('[aria-labelledby="titulo-alta"] input')[
          { fecha: 0, horaInicio: 1, horaFin: 2, motivo: 3 }[n]
        ] ?? document.querySelector('[formControlName="' + n + '"]');
      campo.value = v;
      campo.dispatchEvent(new Event('input', { bubbles: true }));
      campo.dispatchEvent(new Event('blur', { bubbles: true }));
    },
    nombre,
    valor,
  );
}
async function esperarDialogo(titulo) {
  await hasta(
    () =>
      evaluar(
        (t) => document.querySelector('mat-dialog-container h2')?.textContent.trim() === t,
        titulo,
      ),
    'No apareció el diálogo esperado.',
  );
  await hasta(
    () =>
      evaluar(
        () => !!document.querySelector('mat-dialog-container')?.contains(document.activeElement),
      ),
    'El foco no entró en el diálogo.',
  );
}
async function captura(nombre) {
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
  });
  console.log('Navegador local: ' + version.Browser);
  const destino = await comando('Target.createTarget', { url: 'about:blank' });
  sesion = (await comando('Target.attachToTarget', { targetId: destino.targetId, flatten: true }))
    .sessionId;
  await comando('Page.enable');
  await comando('Runtime.enable');
  await navegar('/ingresar', 'Iniciar sesión');
  assert(
    process.env.BT_ADMIN_CORREO && process.env.BT_ADMIN_PASSWORD,
    'Faltan variables ficticias de acceso',
  );
  await campo('correo', process.env.BT_ADMIN_CORREO);
  await campo('password', process.env.BT_ADMIN_PASSWORD);
  await pulsar('Ingresar');
  await hasta(
    () => evaluar(() => location.pathname === '/agenda'),
    'El acceso ADMIN no terminó en agenda.',
  );
  await comando('Emulation.setTimezoneOverride', { timezoneId: 'Europe/Madrid' });
  for (const ancho of [1280, 360]) {
    await comando('Emulation.setDeviceMetricsOverride', {
      width: ancho,
      height: 900,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await navegar('/admin/horarios', 'Horarios y bloqueos');
    await hasta(
      () => evaluar(() => !document.body.textContent.includes('Procesando…')),
      'Carga pendiente.',
    );
    await evaluar(() => document.querySelector('mat-select').click());
    await hasta(
      () => evaluar(() => document.querySelectorAll('mat-option').length > 0),
      'Faltan opciones.',
    );
    await evaluar(() =>
      [...document.querySelectorAll('mat-option')]
        .find((o) => o.textContent.includes('Barbero ficticio T18'))
        .click(),
    );
    await hasta(
      () =>
        evaluar(
          () =>
            document.querySelectorAll('.dia').length === 7 &&
            !document.body.textContent.includes('Procesando…'),
        ),
      'Semana pendiente.',
    );
    assert.equal(
      await evaluar(() => document.documentElement.scrollWidth),
      await evaluar(() => document.documentElement.clientWidth),
      'Desbordamiento horizontal',
    );
    assert.equal(
      await evaluar(() => Intl.DateTimeFormat().resolvedOptions().timeZone),
      'Europe/Madrid',
    );
    assert(
      await evaluar(() =>
        [...document.querySelectorAll('input')].every(
          (campo) =>
            campo.id &&
            (campo.type === 'checkbox' || document.querySelector('label[for="' + campo.id + '"]')),
        ),
      ),
    );
    assert(await evaluar(() => document.body.textContent.includes('El descanso es el hueco')));
    await evaluar(() => document.querySelector('input[type="time"]').focus());
    assert(await evaluar(() => document.activeElement?.getAttribute('type') === 'time'));
    await captura('horarios-' + ancho);
    console.log(
      'Horarios ' +
        ancho +
        ' px: sin desbordamiento; siete días, etiquetas y foco; navegador en Madrid.',
    );
  }
  await pulsar('Copiar el lunes a martes–sábado');
  assert.equal(
    await evaluar(() => document.querySelectorAll('.dia input[type="time"]').length),
    24,
  );
  await pulsar('Guardar semana');
  await hasta(
    () => evaluar(() => document.body.textContent.includes('Semana guardada.')),
    'No se guardó.',
  );
  const fecha = await evaluar(() => {
    const partes = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Lima',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date());
    const p = (tipo) => partes.find((v) => v.type === tipo).value;
    const dia = new Date(p('year') + '-' + p('month') + '-' + p('day') + 'T12:00:00Z');
    dia.setUTCDate(dia.getUTCDate() + 1);
    return dia.toISOString().slice(0, 10);
  });
  await campo('fecha', fecha);
  await campo('horaInicio', '16:00');
  await campo('horaFin', '17:00');
  await campo('motivo', 'Bloqueo visual ficticio T18');
  await pulsar('Agregar bloqueo');
  await hasta(
    () =>
      evaluar(() =>
        document
          .querySelector('.lista-bloqueos')
          .textContent.includes('Bloqueo visual ficticio T18'),
      ),
    'Falta el bloqueo.',
  );
  const texto = await evaluar(() => document.querySelector('.lista-bloqueos').textContent);
  assert(texto.includes('16:00') && texto.includes('17:00'), 'Horas de Lima incorrectas.');
  await evaluar(() => document.querySelector('.lista-bloqueos').scrollIntoView());
  await captura('horarios-bloqueos-360');
  await pulsar('Eliminar');
  await esperarDialogo('Eliminar bloqueo');
  await pulsar('Cancelar');
  await hasta(
    () => evaluar(() => !document.querySelector('mat-dialog-container')),
    'No se canceló.',
  );
  assert(
    await evaluar(() =>
      document.querySelector('.lista-bloqueos').textContent.includes('Bloqueo visual ficticio T18'),
    ),
  );
  await pulsar('Eliminar');
  await esperarDialogo('Eliminar bloqueo');
  await evaluar(() =>
    [...document.querySelectorAll('mat-dialog-container button')]
      .find((b) => b.textContent.trim() === 'Eliminar')
      .click(),
  );
  await hasta(
    () =>
      evaluar(
        () =>
          !document.querySelector('mat-dialog-container') &&
          !document
            .querySelector('.lista-bloqueos')
            .textContent.includes('Bloqueo visual ficticio T18'),
      ),
    'No se eliminó.',
  );
  assert.equal(erroresPagina.length, 0);
  console.log('Copiar/guardar, alta/cancelar/eliminar: correctos; cero excepciones JS.');
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try {
      await comando('Browser.close', {}, undefined);
    } catch {
      /* Edge puede cerrar el canal antes de responder. */
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
