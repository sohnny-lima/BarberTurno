/** Pruebas de aceptación de la CLI en árboles temporales, sin tocar el producto. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve, relative, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOLS = dirname(fileURLToPath(import.meta.url));
const SCRIPT = join(TOOLS, 'medir-java.mjs');
const FIXTURES = join(TOOLS, 'fixtures-medicion');
const ESPERADO = JSON.parse(readFileSync(join(FIXTURES, 'esperado.json'), 'utf8'));

function ejecutar(script, argumentos, cwd = TOOLS) {
  const resultado = spawnSync(process.execPath, [script, ...argumentos], {
    cwd, encoding: 'utf8', timeout: 15_000,
  });
  assert.ifError(resultado.error);
  return resultado;
}

function temporal(t, conFixtures = true) {
  const raiz = mkdtempSync(join(TOOLS, '.medicion-prueba-'));
  t.after(() => {
    // Antes de borrar recursivamente, comprobar la ruta absoluta bajo tools.
    const dentro = relative(resolve(TOOLS), resolve(raiz));
    assert.ok(dentro.startsWith('.medicion-prueba-') && !isAbsolute(dentro)
      && !dentro.startsWith('..') && !dentro.includes('/') && !dentro.includes('\\'));
    rmSync(raiz, { recursive: true, force: true });
  });
  mkdirSync(join(raiz, 'tools'), { recursive: true });
  cpSync(SCRIPT, join(raiz, 'tools/medir-java.mjs'));
  if (conFixtures) {
    for (const ruta of ['backend', 'frontend', 'perf']) cpSync(join(FIXTURES, ruta), join(raiz, ruta), { recursive: true });
  }
  return { raiz, script: join(raiz, 'tools/medir-java.mjs') };
}

test('fixtures conocidos coinciden en cada archivo, clasificación y exclusión', () => {
  const resultado = ejecutar(SCRIPT, ['--verificar-fixtures']);
  assert.equal(resultado.status, 0, resultado.stderr);
  assert.match(resultado.stdout, /14 archivos contados, 7 exclusiones/);
});

test('una diferencia en esperado.json devuelve código 1', t => {
  const { raiz, script } = temporal(t, false);
  const destino = join(raiz, 'tools/fixtures-medicion');
  cpSync(FIXTURES, destino, { recursive: true });
  const esperado = structuredClone(ESPERADO);
  esperado.lenguajes.Java.producto++;
  writeFileSync(join(destino, 'esperado.json'), JSON.stringify(esperado), 'utf8');
  const resultado = ejecutar(script, ['--verificar-fixtures'], raiz);
  assert.equal(resultado.status, 1);
  assert.match(resultado.stderr, /^Fixtures distintos del resultado esperado\./);
  assert.match(resultado.stderr, /"esperado"[\s\S]*"obtenido"/);
});

test('JSON y Markdown miden desde la ubicación del script, aun con otro cwd', t => {
  const { raiz, script } = temporal(t);
  const json = ejecutar(script, ['--json'], join(raiz, 'backend'));
  assert.equal(json.status, 0, json.stderr);
  const obtenido = JSON.parse(json.stdout);
  for (const [clave, valor] of Object.entries(ESPERADO)) assert.deepEqual(obtenido[clave], valor);
  assert.match(obtenido.fecha, /^\d{4}-\d{2}-\d{2}T/);
  assert.match(obtenido.commit, /^[a-f0-9]+$/);
  const markdown = ejecutar(script, [], raiz);
  assert.equal(markdown.status, 0, markdown.stderr);
  assert.match(markdown.stdout, /Java con pruebas:\*\* 39\.62 %/);
  assert.match(markdown.stdout, /Java sin pruebas:\*\* 34\.09 %/);
  assert.match(markdown.stdout, /theme-colors\.scss/);
});

test('--escribir crea la introducción y añade secciones sin reemplazar evidencia', t => {
  const { raiz, script } = temporal(t);
  const primera = ejecutar(script, ['--escribir'], raiz);
  assert.equal(primera.status, 0, primera.stderr);
  const destino = join(raiz, 'docs/pruebas/medicion-java.md');
  const antes = readFileSync(destino, 'utf8');
  assert.match(antes, /RA-02[\s\S]*informativa[\s\S]*P-02 está resuelta/);
  const segunda = ejecutar(script, ['--escribir', '--json'], raiz);
  assert.equal(segunda.status, 0, segunda.stderr);
  JSON.parse(segunda.stdout);
  const despues = readFileSync(destino, 'utf8');
  assert.ok(despues.startsWith(antes));
  assert.equal((despues.match(/^## Medición/gm) || []).length, 2);
  assert.equal((despues.match(/^# Medición del porcentaje/gm) || []).length, 1);
});

test('sin fuentes los porcentajes son null y las raíces ausentes son explícitas', t => {
  const { raiz, script } = temporal(t, false);
  const resultado = ejecutar(script, ['--json'], raiz);
  assert.equal(resultado.status, 0, resultado.stderr);
  const obtenido = JSON.parse(resultado.stdout);
  assert.equal(obtenido.totales.total, 0);
  assert.equal(obtenido.porcentajeJavaConPruebas, null);
  assert.equal(obtenido.porcentajeJavaSinPruebas, null);
  assert.deepEqual(obtenido.ausentes, ['backend/src', 'frontend/src', 'frontend/e2e', 'perf']);
  assert.match(ejecutar(script, [], raiz).stdout, /No aplica \(denominador cero\)/);
});

test('CRLF y última línea sin salto conservan los conteos léxicos', t => {
  const { raiz, script } = temporal(t);
  for (const archivo of ESPERADO.archivos) {
    const ruta = join(raiz, archivo.ruta);
    const texto = readFileSync(ruta, 'utf8').replace(/\n$/, '').replace(/\n/g, '\r\n');
    writeFileSync(ruta, texto, 'utf8');
  }
  const resultado = ejecutar(script, ['--json'], raiz);
  assert.equal(resultado.status, 0, resultado.stderr);
  const obtenido = JSON.parse(resultado.stdout);
  for (const [clave, valor] of Object.entries(ESPERADO)) assert.deepEqual(obtenido[clave], valor);
});

test('dependencias y salidas quedan fuera y se informan sin recorrerlas', t => {
  const { raiz, script } = temporal(t);
  for (const directorio of ['node_modules', 'target', 'dist', '.angular']) {
    const ruta = join(raiz, 'frontend/src', directorio);
    mkdirSync(ruta);
    writeFileSync(join(ruta, 'ignorado.ts'), 'const ignorado = 1;\n', 'utf8');
  }
  const resultado = ejecutar(script, ['--json'], raiz);
  assert.equal(resultado.status, 0, resultado.stderr);
  const obtenido = JSON.parse(resultado.stdout);
  assert.deepEqual(obtenido.totales, ESPERADO.totales);
  assert.equal(obtenido.excluidos.length, ESPERADO.excluidos.length + 4);
});

test('opciones desconocidas o incompatibles fallan de forma explícita', () => {
  const desconocida = ejecutar(SCRIPT, ['--desconocida']);
  assert.equal(desconocida.status, 1);
  assert.match(desconocida.stderr, /Opción desconocida: --desconocida/);
  const incompatible = ejecutar(SCRIPT, ['--verificar-fixtures', '--escribir']);
  assert.equal(incompatible.status, 1);
  assert.match(incompatible.stderr, /--verificar-fixtures se usa sin otras opciones/);
});
