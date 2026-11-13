import { createServer, request } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { createHash } from 'node:crypto';

const raiz = resolve('dist/frontend/browser');
let version = 1;
const tipos = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};
const indice = async () =>
  (await readFile(resolve(raiz, 'index.html'), 'utf8')).replace(
    '</head>',
    `<meta name="version-prueba-pwa" content="${version}"></head>`,
  );

/** Solo servidor local de pruebas: segunda compilación derivada en memoria, sin modificar dist. */
const servidor = createServer(async (entrada, salida) => {
  const url = new URL(entrada.url, 'http://localhost:4301');
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) {
    const proxy = request(
      {
        hostname: 'localhost',
        port: 8081,
        path: entrada.url,
        method: entrada.method,
        headers: { ...entrada.headers, host: 'localhost:8081' },
      },
      (respuesta) => {
        salida.writeHead(respuesta.statusCode, respuesta.headers);
        respuesta.pipe(salida);
      },
    );
    proxy.on('error', () => {
      // Al parar el jar entre proyectos puede cortarse una respuesta ya empezada: sin cabeceras se
      // responde 503; con ellas se corta la conexión. Nunca debe caerse el servidor de pruebas.
      if (salida.headersSent) salida.destroy();
      else {
        salida.writeHead(503);
        salida.end();
      }
    });
    entrada.pipe(proxy);
    return;
  }
  if (url.pathname === '/__pwa/version' && entrada.method === 'POST') {
    version = Number(url.searchParams.get('valor')) === 2 ? 2 : 1;
    salida.writeHead(204);
    salida.end();
    return;
  }
  try {
    salida.setHeader('Cache-Control', 'no-store');
    if (url.pathname === '/ngsw.json') {
      const manifiesto = JSON.parse(await readFile(resolve(raiz, 'ngsw.json'), 'utf8'));
      manifiesto.hashTable['/index.html'] = createHash('sha1')
        .update(await indice())
        .digest('hex');
      manifiesto.appData = { versionPrueba: version };
      salida.setHeader('Content-Type', 'application/json');
      salida.end(JSON.stringify(manifiesto));
      return;
    }
    const archivo = resolve(raiz, '.' + decodeURIComponent(url.pathname));
    if (archivo !== raiz && !archivo.startsWith(raiz + sep)) {
      salida.writeHead(403);
      salida.end();
      return;
    }
    let contenido;
    let tipo = extname(archivo);
    if (url.pathname === '/index.html' || !tipo) {
      contenido = await indice();
      tipo = '.html';
    } else {
      if (!(await stat(archivo)).isFile()) throw new Error('No es archivo');
      contenido = await readFile(archivo);
    }
    salida.setHeader('Content-Type', tipos[tipo] ?? 'application/octet-stream');
    salida.end(contenido);
  } catch {
    salida.writeHead(404);
    salida.end();
  }
});
servidor.listen(4301, 'localhost');
for (const evento of ['SIGTERM', 'SIGINT'])
  process.on(evento, () => servidor.close(() => process.exit(0)));
