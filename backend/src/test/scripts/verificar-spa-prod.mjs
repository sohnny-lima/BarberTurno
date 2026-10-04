// Ensayo T-33 contra un jar ya iniciado con prod; no lee ni escribe secretos.
// Node 24.21.0: node src/test/scripts/verificar-spa-prod.mjs URL ruta-edge directorio-temporal
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { setTimeout as esperar } from 'node:timers/promises';

const [base, edge, directorio] = process.argv.slice(2);
assert(base && edge && directorio, 'Indique URL local, ejecutable Edge y directorio temporal.');
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), 'El ensayo exige un host local.');
const csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
  + "img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'self'; "
  + "form-action 'self'; object-src 'none'; frame-ancestors 'none'";
function cabeceras(respuesta) {
  assert.equal(respuesta.headers.get('content-security-policy'), csp);
  assert.equal(respuesta.headers.get('x-content-type-options'), 'nosniff');
  assert.equal(respuesta.headers.get('x-frame-options'), 'DENY');
  assert.equal(respuesta.headers.get('referrer-policy'), 'strict-origin-when-cross-origin');
  assert.equal(respuesta.headers.get('strict-transport-security'), 'max-age=31536000 ; includeSubDomains');
}
async function obtener(ruta, accept = '*/*') {
  const respuesta = await fetch(new URL(ruta, base), { headers: { Accept: accept } });
  cabeceras(respuesta);
  return respuesta;
}
const health = await obtener('/actuator/health', 'application/json');
assert.equal(health.status, 200);
assert.equal(await health.text(), '{"status":"UP"}');
const principal = await obtener('/', 'text/html');
assert.equal(principal.status, 200);
const html = await principal.text();
assert.match(html, /<app-root>/);
const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];
assert(scripts.length > 0, 'El HTML debe cargar los bundles Angular.');
for (const [, atributos, contenido] of scripts) {
  assert.match(atributos, /\bsrc=/i, 'No se permiten scripts en línea.');
  assert.equal(contenido.trim(), '');
}
const recursos = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)].map(m => m[1]);
for (const ruta of recursos) {
  const recurso = await obtener(ruta);
  assert.equal(recurso.status, 200, ruta);
}
const reservar = await obtener('/reservar', 'text/html');
assert.equal(reservar.status, 200);
assert.equal(await reservar.text(), html);
for (const ruta of ['/api/perfil', '/api/reservas', '/api/usuarios', '/api/x', '/v3/api-docs', '/swagger-ui/index.html', '/actuator/env']) {
  const respuesta = await obtener(ruta, 'text/html');
  assert.equal(respuesta.status, 401, ruta);
  assert.equal((await respuesta.json()).codigo, 'NO_AUTENTICADO', ruta);
}
console.log(`HTTP prod: health y SPA 200; ${recursos.length} recursos 200; scripts en línea=0; rutas protegidas=401; CSP/HSTS exactos.`);

await mkdir(directorio, { recursive: true });
const proceso = spawn(edge, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=0', `--user-data-dir=${resolve(directorio)}`, 'about:blank'],
  { windowsHide: true, stdio: 'ignore' });
let socket;
try {
  let puerto;
  for (let intento = 0; intento < 100; intento++) {
    try { puerto = (await readFile(resolve(directorio, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; break; }
    catch { await esperar(100); }
  }
  assert(puerto, 'Edge no abrió el puerto de depuración.');
  const version = await (await fetch(`http://127.0.0.1:${puerto}/json/version`)).json();
  console.log(`Navegador: ${version.Browser}`);
  socket = new WebSocket(version.webSocketDebuggerUrl);
  await new Promise((ok, fallo) => { socket.addEventListener('open', ok, { once: true }); socket.addEventListener('error', fallo, { once: true }); });
  let secuencia = 0;
  const pendientes = new Map();
  const errores = [];
  const respuestas = [];
  socket.addEventListener('message', evento => {
    const mensaje = JSON.parse(evento.data);
    if (mensaje.id) {
      const pendiente = pendientes.get(mensaje.id);
      if (!pendiente) return;
      pendientes.delete(mensaje.id);
      if (mensaje.error) pendiente.fallo(new Error(mensaje.error.message)); else pendiente.ok(mensaje.result);
    }
    if (mensaje.method === 'Runtime.exceptionThrown') errores.push('Excepción JavaScript');
    if (mensaje.method === 'Log.entryAdded' && /content security|refused|violat/i.test(mensaje.params.entry.text)) errores.push(mensaje.params.entry.text);
    if (mensaje.method === 'Runtime.consoleAPICalled') {
      const texto = mensaje.params.args.map(a => a.value ?? a.description ?? '').join(' ');
      if (/CSP_TEST:|NG\d{4,}|ERROR/i.test(texto)) errores.push(texto);
    }
    if (mensaje.method === 'Network.responseReceived') {
      const r = mensaje.params.response;
      if (r.url.startsWith(base) && /\.js(?:\?|$)/.test(r.url)) respuestas.push({ url: r.url, status: r.status });
    }
  });
  function enviar(method, params = {}, sessionId) {
    return new Promise((ok, fallo) => {
      const id = ++secuencia;
      pendientes.set(id, { ok, fallo });
      socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  }
  const { targetId } = await enviar('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await enviar('Target.attachToTarget', { targetId, flatten: true });
  for (const dominio of ['Page', 'Runtime', 'Log', 'Network']) await enviar(`${dominio}.enable`, {}, sessionId);
  await enviar('Page.addScriptToEvaluateOnNewDocument', { source:
    "document.addEventListener('securitypolicyviolation', e => console.error('CSP_TEST:' + e.violatedDirective));" }, sessionId);
  async function evaluar(expression) {
    const r = await enviar('Runtime.evaluate', { expression, returnByValue: true }, sessionId);
    return r.result.value;
  }
  for (const [ruta, titulo] of [['/', 'Iniciar sesión'], ['/reservar', 'Reservar un turno']]) {
    await enviar('Page.navigate', { url: new URL(ruta, base).href }, sessionId);
    let visible;
    for (let intento = 0; intento < 150; intento++) {
      visible = await evaluar("document.querySelector('h1')?.textContent?.trim()");
      if (visible === titulo) break;
      await esperar(100);
    }
    assert.equal(visible, titulo, `Angular debe renderizar ${ruta}.`);
    assert.equal(await evaluar("document.querySelectorAll('style').length > 0"), true, 'Angular/Material debe insertar sus estilos.');
    console.log(`Angular ${ruta}: ${visible}; estilos dinámicos insertados.`);
  }
  assert(respuestas.length > 0, 'Debe cargar JavaScript desde el jar.');
  assert(respuestas.every(r => r.status === 200), 'Todos los bundles solicitados deben responder 200.');
  assert.deepEqual(errores, [], 'No debe haber excepciones Angular ni violaciones CSP.');
  const captura = await enviar('Page.captureScreenshot', { format: 'png' }, sessionId);
  await writeFile(resolve(directorio, '..', 't33-spa.png'), Buffer.from(captura.data, 'base64'));
  console.log(`Edge: ${respuestas.length} cargas JS 200; excepciones=0; violaciones CSP=0.`);
  await enviar('Browser.close');
} finally {
  socket?.close();
  if (proceso.exitCode === null) {
    proceso.kill();
    await Promise.race([new Promise(ok => proceso.once('exit', ok)), esperar(5000)]);
  }
}
