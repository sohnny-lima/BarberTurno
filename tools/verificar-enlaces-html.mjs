import { readdir, readFile, stat } from 'node:fs/promises';
import { resolve, dirname, join, isAbsolute } from 'node:path';
import { pathToFileURL } from 'node:url';

// El verificador se limita a archivos locales; no hace solicitudes de red.
function decodificarEntidades(valor) {
  const entidades = { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>' };
  return valor.replace(/&(#x[0-9a-f]+|#\d+|amp|quot|apos|lt|gt);/gi, (original, entidad) => {
    if (entidad.startsWith('#')) {
      const codigo = entidad[1].toLowerCase() === 'x'
        ? Number.parseInt(entidad.slice(2), 16) : Number.parseInt(entidad.slice(1), 10);
      return codigo <= 0x10ffff ? String.fromCodePoint(codigo) : original;
    }
    return entidades[entidad.toLowerCase()];
  });
}

async function archivosHtml(directorio) {
  const archivos = [];
  for (const entrada of await readdir(directorio, { withFileTypes: true })) {
    const ruta = join(directorio, entrada.name);
    if (entrada.isDirectory()) archivos.push(...await archivosHtml(ruta));
    else if (entrada.isFile() && /\.html$/i.test(entrada.name)) archivos.push(ruta);
  }
  return archivos.sort();
}

/** Comprueba href/src relativos de HTML estático sin resolver anclas ni consultar la red. */
export async function verificarEnlaces(directorio) {
  const raiz = resolve(directorio);
  if (!(await stat(raiz)).isDirectory()) throw new Error('La ruta debe ser un directorio.');
  const archivos = await archivosHtml(raiz);
  let total = 0;
  const rotos = [];
  for (const archivo of archivos) {
    const html = (await readFile(archivo, 'utf8'))
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/<(script|style)\b([^>]*)>[\s\S]*?<\/\1\s*>/gi, '<$1$2></$1>');
    // Solo atributos dentro de etiquetas: no interpreta texto o código como enlaces.
    for (const etiqueta of html.matchAll(/<[a-z][^>]*>/gi)) {
      for (const atributo of etiqueta[0].matchAll(/(?:^|\s)(href|src)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/gi)) {
        const enlace = decodificarEntidades(atributo[2] ?? atributo[3] ?? atributo[4]).trim();
        if (!enlace || enlace.startsWith('#') || enlace.startsWith('//')
            || /^[a-z][a-z\d+.-]*:/i.test(enlace)) continue;
        const ruta = enlace.split(/[?#]/, 1)[0];
        if (!ruta) continue;
        total++;
        try {
          const destino = decodeURIComponent(ruta);
          const absoluto = isAbsolute(destino) ? resolve(raiz, '.' + destino)
            : resolve(dirname(archivo), destino);
          if (!(await stat(absoluto)).isFile()) throw new Error('No es un archivo.');
        } catch {
          rotos.push({ archivo, enlace });
        }
      }
    }
  }
  return { archivos: archivos.length, total, rotos };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (process.argv.length !== 3) {
    console.error('Uso: node tools/verificar-enlaces-html.mjs <directorio>');
    process.exitCode = 2;
  } else {
    try {
      const resultado = await verificarEnlaces(process.argv[2]);
      console.log(`HTML: ${resultado.archivos}; enlaces relativos: ${resultado.total}; rotos: ${resultado.rotos.length}`);
      for (const roto of resultado.rotos) console.error(`${roto.archivo} -> ${roto.enlace}`);
      process.exitCode = resultado.rotos.length ? 1 : 0;
    } catch (error) {
      console.error(`No se pudo verificar: ${error.message}`);
      process.exitCode = 2;
    }
  }
}
