// Edge local por CDP, sin dependencias añadidas. Ejecutar desde verificar-reportes-http.ps1.
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
    '--user-data-dir=' + path.join(temporal, 'edge-t30-' + puerto),
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
  console.log('Navegador local: ' + version.Browser + '; zona Europe/Madrid; reportes T-30');
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
    process.env.BT_ADMIN_CORREO && process.env.BT_ADMIN_PASSWORD && process.env.BT_T30_FECHA,
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
  await navegar('/admin/reportes', 'Reportes e historial operativo');
  await hasta(
    () => evaluar(() => document.querySelectorAll('.estadistica').length === 7),
    'Faltan las siete tarjetas.',
  );
  await campo('desde', process.env.BT_T30_FECHA);
  await campo('hasta', process.env.BT_T30_FECHA);
  async function elegir(nombre, texto) {
    await evaluar(
      (n) => document.querySelector('app-reportes [formControlName="' + n + '"]').click(),
      nombre,
    );
    await hasta(
      () => evaluar(() => document.querySelectorAll('mat-option').length > 0),
      'No se abrió el selector.',
    );
    await evaluar((t) => {
      const opciones = [...document.querySelectorAll('mat-option')].filter((o) =>
        o.textContent.includes(t),
      );
      if (opciones.length !== 1) throw new Error('Opción no única.');
      opciones[0].click();
    }, texto);
  }
  await elegir('servicioId', 'Corte T30');
  await elegir('barberoId', 'Profesional ficticio T30');
  const indice = peticiones.length;
  await pulsar('Aplicar');
  await hasta(
    () => evaluar(() => document.querySelectorAll('app-reportes tbody tr').length === 3),
    'No se mostraron tres reservas.',
  );
  const consultas = peticiones.slice(indice);
  assert.equal(consultas.length, 2, 'Aplicar no emitió ambas consultas.');
  for (const consulta of consultas) {
    assert.equal(consulta.searchParams.get('desde'), process.env.BT_T30_FECHA);
    assert.equal(consulta.searchParams.get('hasta'), process.env.BT_T30_FECHA);
    assert.equal(consulta.searchParams.get('servicioId'), process.env.BT_T30_SERVICIO_ID);
    assert.equal(consulta.searchParams.get('barberoId'), process.env.BT_T30_BARBERO_ID);
  }
  assert(
    await evaluar(() => {
      const filas = [...document.querySelectorAll('app-reportes tbody tr')];
      const chips = [...document.querySelectorAll('.estadisticas app-estado-reserva-chip')];
      return (
        filas.every(
          (f) =>
            f.textContent.includes('Cliente ficticio T30') &&
            f.textContent.includes('Profesional ficticio T30'),
        ) &&
        filas[0].textContent.includes('09:00') &&
        chips.length === 6 &&
        document.querySelector('.estadistica strong').textContent.trim() === '3' &&
        document
          .querySelector('.conciliacion')
          .textContent.includes('Los estados suman el total') &&
        document.querySelectorAll('progress').length === 2 &&
        document.querySelector('[formControlName=servicioId]').textContent.includes('(inactivo)')
      );
    }),
    'Faltan campos, horas Lima, tarjetas, conciliación o barras.',
  );
  await evaluar(() => document.querySelector('app-reportes').scrollIntoView());
  await captura('t30-1440-reportes');
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
    'Desbordamiento horizontal de página a 360 px.',
  );
  assert(
    await evaluar(() => {
      const tabla = document.querySelector('.tabla-scroll');
      return (
        tabla.scrollWidth > tabla.clientWidth &&
        getComputedStyle(tabla).overflowX === 'auto' &&
        tabla.tabIndex === 0
      );
    }),
    'La tabla no ofrece desplazamiento controlado y foco.',
  );
  await captura('t30-360-reportes');
  await evaluar(() => document.querySelector('.tabla-scroll').scrollIntoView());
  await captura('t30-360-historial');
  const sinConsulta = peticiones.length;
  await campo('desde', '2026-10-05');
  await campo('hasta', '2026-10-04');
  await pulsar('Aplicar');
  assert(
    await evaluar(() =>
      document.querySelector('#error-periodo').textContent.includes('Desde debe ser anterior'),
    ),
    'Falta error de orden del periodo.',
  );
  assert.equal(peticiones.length, sinConsulta, 'El rango invertido consultó la API.');
  await campo('desde', '2024-01-01');
  await campo('hasta', '2025-01-01');
  await pulsar('Aplicar');
  assert(
    await evaluar(() => document.querySelector('#error-periodo').textContent.includes('366 días')),
    'Falta error de extensión.',
  );
  assert.equal(peticiones.length, sinConsulta, 'El rango de 367 días consultó la API.');
  assert.equal(erroresPagina.length, 0, 'Excepciones JavaScript en la página.');
  console.log(
    'QA reportes aprobada: 1440/360 px, Madrid conserva Lima, seis estados y ceros, catálogo inactivo, barras etiquetadas, tabla con desplazamiento y filtros inválidos sin HTTP.',
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
