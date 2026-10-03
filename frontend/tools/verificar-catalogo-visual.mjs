// Edge local por CDP, sin dependencias añadidas. Ejecutar desde verificar-catalogo-http.ps1.
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
    '--user-data-dir=' + path.join(temporal, 'edge-t17-' + puerto),
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
      const campo = document.querySelector('[formControlName="' + n + '"]');
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
async function comprobarTabla(recurso, ancho) {
  await comando('Emulation.setDeviceMetricsOverride', {
    width: ancho,
    height: 800,
    deviceScaleFactor: 1,
    mobile: false,
  });
  await navegar('/admin/' + recurso.toLowerCase(), recurso);
  await hasta(
    () => evaluar(() => document.querySelectorAll('tr[mat-row]').length === 1),
    'La tabla no mostró el registro ficticio.',
  );
  const medidas = await evaluar(() => {
    const tabla = document.querySelector('.tabla-contenedor');
    const rect = tabla.getBoundingClientRect();
    return {
      pagina: document.documentElement.scrollWidth,
      anchoContenido: document.documentElement.clientWidth,
      viewport: innerWidth,
      tablaVisible: tabla.clientWidth,
      tablaContenido: tabla.scrollWidth,
      izquierda: rect.left,
      derecha: rect.right,
      accesible: tabla.tabIndex === 0 && !!tabla.getAttribute('aria-label'),
    };
  });
  assert.equal(medidas.pagina, medidas.anchoContenido, 'La página desborda horizontalmente');
  assert.equal(medidas.viewport, ancho);
  assert(medidas.izquierda >= 0 && medidas.derecha <= ancho);
  assert(medidas.accesible, 'La tabla no tiene región enfocada y etiquetada');
  if (ancho === 360) assert(medidas.tablaContenido > medidas.tablaVisible);
  if (recurso === 'Servicios') {
    assert(
      await evaluar(() =>
        document.querySelector('td.mat-column-precio').textContent.includes('S/'),
      ),
    );
    assert(
      await evaluar(() =>
        document.querySelector('td.mat-column-precio').textContent.includes('25.50'),
      ),
    );
  }
  await captura(recurso.toLowerCase() + '-' + ancho);
  console.log(
    recurso +
      ' ' +
      ancho +
      'px: página=' +
      medidas.pagina +
      ', tabla=' +
      medidas.tablaVisible +
      '/' +
      medidas.tablaContenido +
      ', región accesible.',
  );
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
  for (const ancho of [1280, 360]) {
    await comprobarTabla('Servicios', ancho);
    await comprobarTabla('Barberos', ancho);
  }
  await comprobarTabla('Servicios', 360);
  const nombreServicio = await evaluar(
    () => document.querySelector('td.mat-column-nombre strong').textContent,
  );
  await pulsar('Nuevo servicio');
  await esperarDialogo('Nuevo servicio');
  const dialogo = await evaluar(() => {
    const r = document.querySelector('mat-dialog-container').getBoundingClientRect();
    return {
      izquierda: r.left,
      derecha: r.right,
      etiquetas: document.querySelectorAll('mat-dialog-container mat-label').length,
    };
  });
  assert(dialogo.izquierda >= 0 && dialogo.derecha <= 360);
  assert.equal(dialogo.etiquetas, 4);
  assert(
    await evaluar(
      () =>
        getComputedStyle(document.querySelector('mat-dialog-content.formulario')).display ===
        'flex',
    ),
    'El contenido del formulario debe conservar su distribución en columna.',
  );
  await captura('servicio-dialogo-360');
  await campo('nombre', nombreServicio);
  await pulsar('Guardar servicio');
  await hasta(
    () =>
      evaluar(() =>
        [...document.querySelectorAll('mat-error')].some((e) => e.textContent.includes('nombre')),
      ),
    'El nombre duplicado no se presentó junto al campo.',
  );
  await pulsar('Cancelar');
  await hasta(
    () => evaluar(() => !document.querySelector('mat-dialog-container')),
    'El diálogo no cerró.',
  );
  await comprobarTabla('Barberos', 360);
  await pulsar('Nuevo barbero');
  await esperarDialogo('Nuevo barbero');
  assert(
    await evaluar(() => {
      const ayuda = document.querySelector('mat-dialog-container mat-hint').getBoundingClientRect();
      const generar = [...document.querySelectorAll('mat-dialog-container button')]
        .find((b) => b.textContent.trim() === 'Generar')
        .getBoundingClientRect();
      return ayuda.bottom <= generar.top;
    }),
    'La ayuda de contraseña se superpone con Generar.',
  );
  assert(
    await evaluar(() => {
      const campos = [...document.querySelectorAll('mat-dialog-container mat-form-field')];
      return campos
        .slice(1)
        .every(
          (campo, indice) =>
            campo.getBoundingClientRect().top - campos[indice].getBoundingClientRect().bottom >= 11,
        );
    }),
    'Los campos deben conservar la separación del formulario.',
  );
  await captura('barbero-dialogo-360');
  await pulsar('Generar');
  assert(
    await evaluar(() => {
      const p = document.querySelector('[formControlName="passwordTemporal"]').value;
      return p.length === 16 && /[a-zA-Z]/.test(p) && /[0-9]/.test(p);
    }),
    'La contraseña generada no cumple la política.',
  );
  await evaluar(() => document.querySelector('mat-select').click());
  await hasta(
    () => evaluar(() => document.querySelectorAll('mat-option').length === 2),
    'No abrió la selección de alta.',
  );
  await evaluar(() =>
    [...document.querySelectorAll('mat-option')]
      .find((o) => o.textContent.trim() === 'Vincular administrador')
      .click(),
  );
  await hasta(
    () => evaluar(() => !!document.querySelector('[formControlName="usuarioId"]')),
    'No cambió al vínculo ADMIN.',
  );
  assert(await evaluar(() => !document.querySelector('[formControlName="passwordTemporal"]')));
  await pulsar('Cancelar');
  assert.equal(erroresPagina.length, 0, 'Hay excepciones JavaScript');
  console.log(
    'Diálogos 360px: etiquetas y foco; duplicado por campo; generación y vínculo ADMIN correctos; cero excepciones JavaScript.',
  );
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
