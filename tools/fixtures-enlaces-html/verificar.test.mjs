import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { verificarEnlaces } from '../verificar-enlaces-html.mjs';

const ruta = (relativa) => fileURLToPath(new URL(relativa, import.meta.url));

test('un destino local válido con query y ancla pasa; omite enlaces externos, comentarios y scripts', async () => {
  const resultado = await verificarEnlaces(ruta('./bueno/'));
  assert.equal(resultado.archivos, 2);
  assert.equal(resultado.total, 1);
  assert.deepEqual(resultado.rotos, []);
});

test('un destino inexistente se identifica y la CLI termina con código 1', async () => {
  const resultado = await verificarEnlaces(ruta('./roto/'));
  assert.equal(resultado.total, 1);
  assert.equal(resultado.rotos.length, 1);
  assert.equal(resultado.rotos[0].enlace, 'ausente.html#parte');
  const cli = spawnSync(process.execPath, [ruta('../verificar-enlaces-html.mjs'), ruta('./roto/')], { encoding: 'utf8' });
  assert.equal(cli.status, 1);
  assert.match(cli.stdout, /rotos: 1/);
  assert.match(cli.stderr, /ausente\.html/);
});

test('la CLI devuelve 0 para el fixture válido y 2 si falta el directorio', () => {
  const valida = spawnSync(process.execPath, [ruta('../verificar-enlaces-html.mjs'), ruta('./bueno/')], { encoding: 'utf8' });
  assert.equal(valida.status, 0);
  const sinArgumento = spawnSync(process.execPath, [ruta('../verificar-enlaces-html.mjs')], { encoding: 'utf8' });
  assert.equal(sinArgumento.status, 2);
});
