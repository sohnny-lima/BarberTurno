// Utilidades de formato para el informe APF3 (docx 9).
const fs = require('fs');
const path = require('path');
const d = require('docx');
const {
  Paragraph, TextRun, HeadingLevel, AlignmentType, Table, TableRow, TableCell, WidthType, BorderStyle,
  ShadingType, ImageRun, PageBreak, LevelFormat, ExternalHyperlink, TableLayoutType, VerticalAlign,
} = d;

const VERDE = '145C43';
const GRIS = '4A5A54';
const ANCHO = 9026; // A4 con márgenes de 2,5 cm: 11906 - 2*1440 = 9026 DXA aprox.
const FUENTE = 'Calibri';
const MONO = 'Consolas';

// Texto con marcas ligeras: **negrita**, `código`, _cursiva_, [texto](url)
function runs(texto, base = {}) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`|_[^_]+_|\[[^\]]+\]\([^)]+\))/g;
  let i = 0, m;
  while ((m = re.exec(texto))) {
    if (m.index > i) out.push(new TextRun({ text: texto.slice(i, m.index), ...base }));
    const t = m[0];
    if (t.startsWith('**')) out.push(new TextRun({ text: t.slice(2, -2), bold: true, ...base }));
    else if (t.startsWith('`')) out.push(new TextRun({ text: t.slice(1, -1), font: MONO, size: (base.size || 22) - 2, ...base, ...{ font: MONO } }));
    else if (t.startsWith('_')) out.push(new TextRun({ text: t.slice(1, -1), italics: true, ...base }));
    else {
      const mm = t.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      out.push(new ExternalHyperlink({ link: mm[2], children: [new TextRun({ text: mm[1], style: 'Hyperlink', ...base })] }));
    }
    i = m.index + t.length;
  }
  if (i < texto.length) out.push(new TextRun({ text: texto.slice(i), ...base }));
  return out;
}

const p = (texto, opt = {}) => new Paragraph({
  children: runs(texto, opt.run || {}),
  spacing: { after: opt.after ?? 120, before: opt.before ?? 0, line: 276 },
  alignment: opt.align || (/`[^`]{28,}`/.test(texto) ? AlignmentType.LEFT : AlignmentType.JUSTIFIED),
  keepNext: opt.keepNext,
  indent: opt.indent,
});
const h1 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_1, pageBreakBefore: true });
const h1Continuo = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_1 });
const h2 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_2, keepNext: true });
const h3 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_3, keepNext: true });
const vinetas = (items, nivel = 0) => items.map((t) => new Paragraph({
  children: runs(t), numbering: { reference: 'vinetas', level: nivel }, spacing: { after: 60, line: 264 },
  alignment: AlignmentType.LEFT,
}));
let instanciaLista = 0;
const numerada = (items) => { instanciaLista += 1; const n = instanciaLista; return items.map((t) => new Paragraph({
  children: runs(t), numbering: { reference: 'numeros', level: 0, instance: n }, spacing: { after: 60, line: 264 },
})); };
const salto = () => new Paragraph({ children: [new PageBreak()] });

const borde = { style: BorderStyle.SINGLE, size: 4, color: 'C9D3CF' };
const bordes = { top: borde, bottom: borde, left: borde, right: borde };

// Tabla con anchos relativos (suman 100).
function tabla(cabecera, filas, pesos, opt = {}) {
  const total = pesos.reduce((a, b) => a + b, 0);
  const anchos = pesos.map((w) => Math.floor((w / total) * ANCHO));
  anchos[anchos.length - 1] += ANCHO - anchos.reduce((a, b) => a + b, 0);
  const tam = opt.size || 18;
  const celda = (texto, i, esCab, sombra) => new TableCell({
    width: { size: anchos[i], type: WidthType.DXA },
    borders: bordes,
    shading: esCab ? { type: ShadingType.CLEAR, color: 'auto', fill: 'E3EEE8' } : (sombra ? { type: ShadingType.CLEAR, color: 'auto', fill: sombra } : undefined),
    margins: { top: 50, bottom: 50, left: 90, right: 90 },
    verticalAlign: VerticalAlign.TOP,
    children: String(texto).split('\n').map((linea) => new Paragraph({
      children: runs(linea, { size: tam, bold: esCab || undefined }), spacing: { after: 30, line: 250 }, alignment: AlignmentType.LEFT,
    })),
  });
  const filasTabla = [new TableRow({ tableHeader: true, cantSplit: true, children: cabecera.map((c, i) => celda(c, i, true)) })];
  filas.forEach((f) => filasTabla.push(new TableRow({ cantSplit: true, children: f.map((c, i) => celda(c, i, false, opt.sombra && opt.sombra(f, i))) })));
  return [new Table({ width: { size: ANCHO, type: WidthType.DXA }, columnWidths: anchos, layout: TableLayoutType.FIXED, rows: filasTabla }),
    new Paragraph({ spacing: { after: 120 }, children: [] })];
}

// Bloque de código monoespaciado con fondo.
function codigo(texto, titulo) {
  const lineas = [];
  for (const l of texto.replace(/\t/g, '    ').split('\n')) {
    let resto = l;
    const sangria = l.match(/^ */)[0].length + 8;
    while (resto.length > 100) {
      let corte = resto.lastIndexOf(' ', 100);
      if (corte < sangria + 10) corte = 100;
      lineas.push(resto.slice(0, corte));
      resto = ' '.repeat(sangria) + resto.slice(corte).trimStart();
    }
    lineas.push(resto);
  }
  const parrafos = lineas.map((l) => new Paragraph({
    children: [new TextRun({ text: l.length ? l : ' ', font: MONO, size: 15, color: '1E2B26' })],
    spacing: { after: 0, line: 228 },
    alignment: AlignmentType.LEFT,
  }));
  const out = [];
  if (titulo) out.push(new Paragraph({ children: [new TextRun({ text: titulo, bold: true, size: 18, color: GRIS })], spacing: { before: 60, after: 40 }, keepNext: true }));
  out.push(new Table({
    width: { size: ANCHO, type: WidthType.DXA }, columnWidths: [ANCHO], layout: TableLayoutType.FIXED,
    rows: [new TableRow({ cantSplit: true, children: [new TableCell({
      width: { size: ANCHO, type: WidthType.DXA }, borders: bordes,
      shading: { type: ShadingType.CLEAR, color: 'auto', fill: 'F4F7F5' },
      margins: { top: 80, bottom: 80, left: 140, right: 140 }, children: parrafos,
    })] })],
  }));
  out.push(new Paragraph({ spacing: { after: 120 }, children: [] }));
  return out;
}

function dimensiones(archivo) {
  const b = fs.readFileSync(archivo);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}

let numFigura = 0;
// Figura centrada con pie numerado. anchoCm: ancho deseado.
function figura(archivo, pie, anchoCm = 16, maxAltoCm = 21) {
  const { w, h } = dimensiones(archivo);
  let ancho = anchoCm * 37.8; let alto = ancho * h / w;
  const maxAlto = maxAltoCm * 37.8;
  if (alto > maxAlto) { alto = maxAlto; ancho = alto * w / h; }
  numFigura += 1;
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, keepNext: true, spacing: { before: 80, after: 40 },
      children: [new ImageRun({ type: 'png', data: fs.readFileSync(archivo), transformation: { width: Math.round(ancho), height: Math.round(alto) },
        altText: { title: `Figura ${numFigura}`, description: pie, name: path.basename(archivo) } })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 160 },
      children: runs(`Figura ${numFigura}. ${pie}`, { italics: true, size: 18, color: GRIS }) }),
  ];
}

// Varias capturas en una fila (tabla sin bordes) con un único pie.
function figuraFila(archivos, pie, altoCm = 9) {
  const n = archivos.length;
  const anchoCol = Math.floor(ANCHO / n);
  const sinBorde = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
  const celdas = archivos.map((a) => {
    const { w, h } = dimensiones(a);
    let alto = altoCm * 37.8; let ancho = alto * w / h;
    const maxAncho = (anchoCol / 1440) * 2.54 * 37.8 - 8;
    if (ancho > maxAncho) { ancho = maxAncho; alto = ancho * h / w; }
    return new TableCell({ width: { size: anchoCol, type: WidthType.DXA },
      borders: { top: sinBorde, bottom: sinBorde, left: sinBorde, right: sinBorde },
      children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new ImageRun({ type: 'png', data: fs.readFileSync(a),
        transformation: { width: Math.round(ancho), height: Math.round(alto) }, altText: { title: path.basename(a), description: pie, name: path.basename(a) } })] })] });
  });
  numFigura += 1;
  return [
    new Table({ width: { size: ANCHO, type: WidthType.DXA }, columnWidths: Array(n).fill(anchoCol), layout: TableLayoutType.FIXED,
      rows: [new TableRow({ cantSplit: true, children: celdas })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 40, after: 160 },
      children: runs(`Figura ${numFigura}. ${pie}`, { italics: true, size: 18, color: GRIS }) }),
  ];
}

const nota = (texto) => new Table({
  width: { size: ANCHO, type: WidthType.DXA }, columnWidths: [ANCHO],
  rows: [new TableRow({ children: [new TableCell({ width: { size: ANCHO, type: WidthType.DXA },
    borders: { top: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }, bottom: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }, right: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }, left: { style: BorderStyle.SINGLE, size: 24, color: 'D9A441' } },
    shading: { type: ShadingType.CLEAR, color: 'auto', fill: 'FFF7E6' }, margins: { top: 80, bottom: 80, left: 160, right: 120 },
    children: [new Paragraph({ children: runs(texto, { size: 20 }), spacing: { after: 0, line: 264 } })] })] })],
});
const notaBloque = (t) => [nota(t), new Paragraph({ spacing: { after: 120 }, children: [] })];

module.exports = { d, runs, p, h1, h1Continuo, h2, h3, vinetas, numerada, salto, tabla, codigo, figura, figuraFila, notaBloque, VERDE, GRIS, ANCHO, FUENTE, MONO };
