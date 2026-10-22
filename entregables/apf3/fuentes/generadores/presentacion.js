// Genera APF3_BarberTurno_Sustentacion.pptx. Uso: node presentacion.js <salida.pptx>
const fs = require('fs');
const path = require('path');
const pptxgen = require('pptxgenjs');
const { applyTheme } = require('C:/Users/limas/.claude/skills/synced/6def205e-4e62-4642-817d-2057cbdc4a3a_5594084e-e993-4efb-aa01-8542e9f6cfb0/pptx/scripts/apply_theme.js');

const S = path.resolve(__dirname, '..');
const EV = path.join(S, 'apf3', 'evidencias');
const T58 = JSON.parse(fs.readFileSync(path.join(__dirname, 't58.json'), 'utf8'));
const CAP = T58.capturas;
const img = (...p) => path.join(...p);

const THEME = {
  name: 'BarberTurno verde',
  headFontFace: 'Calibri', bodyFontFace: 'Calibri',
  colors: { dk1: '14231D', lt1: 'FFFFFF', dk2: '145C43', lt2: 'EEF5F1',
    accent1: '145C43', accent2: 'D9A441', accent3: '4A5D9A', accent4: '8A1F1F', accent5: '4A5A54', accent6: '9FB3AA',
    hlink: '145C43', folHlink: '4A5A54' },
};
const pres = new pptxgen();
pres.layout = 'LAYOUT_WIDE'; // 13.33 x 7.5
pres.author = 'Sohnny Walter Lima Infanzón';
pres.title = 'BarberTurno · APF3 · Sustentación';
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
const C = pres.SchemeColor;

// ---------- Layouts
pres.defineSlideMaster({ title: 'PORTADA', background: { color: '145C43' }, objects: [
  { placeholder: { options: { name: 'title', type: 'title', x: 0.8, y: 2.0, w: 11.7, h: 1.4, fontSize: 54, bold: true, color: 'FFFFFF', valign: 'bottom', align: 'left' }, text: '' } },
  { placeholder: { options: { name: 'body', type: 'body', x: 0.8, y: 3.5, w: 11.7, h: 2.6, fontSize: 20, color: 'EEF5F1', valign: 'top', align: 'left' }, text: '' } },
] });
pres.defineSlideMaster({ title: 'CONTENIDO', background: { color: 'FFFFFF' }, margin: [0.5, 0.6, 0.6, 0.6], objects: [
  { placeholder: { options: { name: 'title', type: 'title', x: 0.6, y: 0.35, w: 12.1, h: 0.85, fontSize: 34, bold: true, color: '145C43', valign: 'middle', margin: 0, align: 'left' }, text: '' } },
  { text: { text: 'BarberTurno · APF3 · Integrador I', options: { x: 0.6, y: 7.0, w: 6, h: 0.3, fontSize: 10, color: '4A5A54', margin: 0 } } },
], slideNumber: { x: 12.2, y: 7.0, w: 0.6, h: 0.3, fontSize: 10, color: '4A5A54' } });
pres.defineSlideMaster({ title: 'CIERRE', background: { color: '14231D' }, objects: [
  { placeholder: { options: { name: 'title', type: 'title', x: 0.6, y: 0.35, w: 12.1, h: 0.85, fontSize: 34, bold: true, color: 'FFFFFF', valign: 'middle', margin: 0, align: 'left' }, text: '' } },
], slideNumber: { x: 12.2, y: 7.0, w: 0.6, h: 0.3, fontSize: 10, color: 'C9D3CF' } });

let nObj = 0;
const nom = (p) => `${p}-${++nObj}`;
const nueva = (titulo, seccion, master = 'CONTENIDO') => {
  const s = pres.addSlide({ masterName: master, sectionTitle: seccion });
  s.addText(titulo, { placeholder: 'title' });
  return s;
};
const texto = (s, t, o) => s.addText(t, { isTextBox: true, fontSize: 16, color: '14231D', valign: 'top', objectName: nom('texto'), ...o });
const tarjeta = (s, x, y, w, h, fill = 'EEF5F1') => s.addShape(pres.shapes.ROUNDED_RECTANGLE,
  { x, y, w, h, rectRadius: 0.12, fill: { color: fill }, line: { color: 'D6E3DC', width: 1 }, objectName: nom('tarjeta') });
const insignia = (s, x, y, t, fill = '145C43', d = 0.55) => {
  s.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: fill }, line: { color: fill }, objectName: nom('insignia') });
  s.addText(t, { isTextBox: true, x, y, w: d, h: d, align: 'center', valign: 'middle', fontSize: d > 0.5 ? 18 : 13, bold: true, color: 'FFFFFF', margin: 0, objectName: nom('insignia-texto') });
};
const imagen = (s, archivo, x, y, w, h, o = {}) => {
  const b = fs.readFileSync(archivo); const iw = b.readUInt32BE(16), ih = b.readUInt32BE(20);
  let ww = w, hh = w * ih / iw; if (hh > h) { hh = h; ww = h * iw / ih; }
  const dx = o.alinear === 'izq' ? 0 : (w - ww) / 2;
  s.addImage({ path: archivo, x: x + dx, y, w: ww, h: hh, altText: o.alt || path.basename(archivo), objectName: nom('imagen'),
    ...(o.marco ? { line: { color: 'C9D3CF', width: 1 } } : {}) });
  return { w: ww, h: hh };
};
const cifra = (s, x, y, w, num, etiqueta, color = '145C43') => {
  texto(s, num, { x, y, w, h: 0.9, fontSize: 44, bold: true, color, margin: 0 });
  texto(s, etiqueta, { x, y: y + 0.9, w, h: 0.6, fontSize: 13, color: '4A5A54', margin: 0 });
};

// 1 · Portada
pres.addSection({ title: 'Apertura' });
let s = pres.addSlide({ masterName: 'PORTADA', sectionTitle: 'Apertura' });
s.addText('BarberTurno', { placeholder: 'title' });
s.addText([
  { text: 'Avance de Proyecto Final 3 · Del diseño al sistema funcionando', options: { bold: true, fontSize: 24, breakLine: true } },
  { text: ' ', options: { breakLine: true } },
  { text: 'Sohnny Walter Lima Infanzón', options: { breakLine: true } },
  { text: 'Integrador I: Sistemas Software · Sección 20058', options: { breakLine: true } },
  { text: 'Docente: Mg. Ing. Juan Barahona Sánchez · Universidad Tecnológica del Perú', options: {} },
], { placeholder: 'body' });
s.addNotes('Buenas noches, profesor. Presento el tercer avance de BarberTurno, un sistema web de reservas para una barbería de una sede en Huamanga. En el APF2 presenté el diseño y un prototipo que simulaba las pantallas; hoy muestro el sistema construido, probado y desplegado.');

// 2 · Del prototipo al sistema
pres.addSection({ title: 'Contexto' });
s = nueva('Del prototipo del APF2 al sistema del APF3', 'Contexto');
tarjeta(s, 0.6, 1.45, 5.6, 2.55, 'F4F7F5');
texto(s, 'APF2 · diseño', { x: 0.85, y: 1.6, w: 5.2, h: 0.45, fontSize: 20, bold: true, color: '4A5A54', margin: 0 });
texto(s, [
  { text: 'Tres alternativas con ocho pantallas cada una', options: { bullet: true, breakLine: true } },
  { text: 'Prototipo HTML con datos simulados', options: { bullet: true, breakLine: true } },
  { text: 'Tres clases Java de reglas temporales', options: { bullet: true } },
], { x: 0.85, y: 2.1, w: 5.2, h: 1.8, fontSize: 16, paraSpaceAfter: 6 });
tarjeta(s, 7.1, 1.45, 5.6, 2.55);
texto(s, 'APF3 · construcción', { x: 7.35, y: 1.6, w: 5.2, h: 0.45, fontSize: 20, bold: true, color: '145C43', margin: 0 });
texto(s, [
  { text: 'Angular 22 + Spring Boot 4.1.1 (Java 21) + PostgreSQL 18', options: { bullet: true, breakLine: true } },
  { text: 'RF-01…RF-14 implementados y siete mejoras', options: { bullet: true, breakLine: true } },
  { text: 'Pruebas, CI en GitHub y despliegue en Azure', options: { bullet: true } },
], { x: 7.35, y: 2.1, w: 5.2, h: 1.8, fontSize: 16, paraSpaceAfter: 6 });
s.addShape(pres.shapes.RIGHT_ARROW, { x: 6.35, y: 2.45, w: 0.6, h: 0.55, fill: { color: 'D9A441' }, line: { color: 'D9A441' }, objectName: nom('flecha') });
cifra(s, 0.6, 4.45, 2.9, '21', 'requisitos funcionales en el código');
cifra(s, 3.65, 4.45, 2.9, '3', 'roles: cliente, barbero y administrador');
cifra(s, 6.7, 4.45, 2.9, `${T58.commitsMain}`, 'commits en GitHub (main)');
cifra(s, 9.75, 4.45, 2.9, '19/10', 'desplegado en Azure for Students');
texto(s, 'Alcance sin cambios: una sede, hasta diez barberos. WhatsApp, IA y multitenencia son ideas futuras, no implementadas.', { x: 0.6, y: 6.25, w: 12.1, h: 0.5, fontSize: 14, italic: true, color: '4A5A54' });
s.addNotes('El APF2 era diseño y prototipo; el APF3 es el producto funcionando con evidencias por commit. El alcance no cambió: tres roles, una sede y hasta diez barberos. Las ideas de WhatsApp, IA o varias barberías quedan como evolución futura.');

// 3 · Arquitectura
pres.addSection({ title: 'Diseño' });
s = nueva('Arquitectura implementada: monolito modular', 'Diseño');
imagen(s, img(EV, 'diagramas', 'arquitectura.png'), 0.6, 1.35, 12.1, 5.45, { alt: 'Diagrama de arquitectura de BarberTurno' });
s.addNotes('Un solo jar sirve la interfaz Angular y la API. Las capas son: filtros de seguridad, controladores REST, servicios de dominio con transacciones y repositorios JPA sobre PostgreSQL. Está desplegado en Azure App Service con PostgreSQL administrado. El monolito permite transacciones ACID que guardan reserva, auditoría y aviso juntos.');

// 4 · Recorrido de una petición
s = nueva('Recorrido de una reserva por el sistema', 'Diseño');
const pasos = [
  ['Angular', 'POST /api/reservas con la cookie de sesión y X-XSRF-TOKEN'],
  ['Spring Security', 'Valida JWT, CSRF y rol; si falla: 401 o 403'],
  ['ReservaController', 'Recibe el DTO validado y delega en el servicio'],
  ['ReservaService', 'Transacción: bloquea cliente y barbero y revalida la franja'],
  ['Repositorios', 'Guarda reserva, auditoría y avisos; COMMIT o ROLLBACK'],
];
pasos.forEach(([t, d], i) => {
  const x = 0.6 + i * 2.5;
  tarjeta(s, x, 1.7, 2.25, 3.2, i === 3 ? 'E3EEE8' : 'F4F7F5');
  insignia(s, x + 0.85, 1.95, String(i + 1));
  texto(s, t, { x: x + 0.15, y: 2.7, w: 1.95, h: 0.5, fontSize: 17, bold: true, color: '145C43', align: 'center', margin: 0 });
  texto(s, d, { x: x + 0.15, y: 3.25, w: 1.95, h: 1.55, fontSize: 14, align: 'center', margin: 0 });
  if (i < 4) s.addShape(pres.shapes.RIGHT_ARROW, { x: x + 2.27, y: 3.1, w: 0.21, h: 0.35, fill: { color: 'D9A441' }, line: { color: 'D9A441' }, objectName: nom('flecha') });
});
tarjeta(s, 0.6, 5.3, 12.1, 1.35, 'FFF7E6');
texto(s, [{ text: 'Respuesta: ', options: { bold: true } }, { text: '201 con ReservaDto y sus permisos (la interfaz habilita botones según el servidor), o problem+json con el motivo, por ejemplo 409 FRANJA_NO_DISPONIBLE.' }],
  { x: 0.85, y: 5.45, w: 11.6, h: 1.05, fontSize: 16, valign: 'middle' });
s.addNotes('Recorrido: Angular envía el POST con la cookie y el token CSRF; Spring Security valida JWT, CSRF y rol; el controlador recibe el DTO validado y delega; el servicio abre la transacción, bloquea al cliente y al barbero, revalida la disponibilidad y guarda reserva, auditoría y avisos. La respuesta incluye los permisos calculados por el servidor.');

// 5 · MVC y DAO
s = nueva('MVC y DAO aplicados en el código', 'Diseño');
tarjeta(s, 0.6, 1.4, 5.9, 5.3);
insignia(s, 0.85, 1.6, 'M');
texto(s, 'MVC con Spring Web MVC', { x: 1.55, y: 1.65, w: 4.8, h: 0.5, fontSize: 20, bold: true, color: '145C43', margin: 0 });
texto(s, [
  { text: 'Controlador: ', options: { bold: true } }, { text: '@RestController recibe y valida; no tiene reglas', options: { breakLine: true } },
  { text: 'Modelo: ', options: { bold: true } }, { text: 'servicios, entidades y DTO con las reglas', options: { breakLine: true } },
  { text: 'Vista: ', options: { bold: true } }, { text: 'el JSON que Angular presenta; no hay Thymeleaf', options: {} },
], { x: 0.85, y: 2.35, w: 5.4, h: 1.7, fontSize: 15, paraSpaceAfter: 8 });
texto(s, '@GetMapping("/api/reportes/resumen")\npublic ResumenReporteDto resumen(...) {\n    return reportes.resumen(actor, desde,\n            hasta, servicioId, barberoId);\n}',
  { x: 0.85, y: 4.25, w: 5.4, h: 2.2, fontSize: 13, fontFace: 'Consolas', color: '14231D', fill: { color: 'FFFFFF' }, margin: 0.12 });
tarjeta(s, 6.85, 1.4, 5.85, 5.3);
insignia(s, 7.1, 1.6, 'D');
texto(s, 'DAO con Spring Data JPA', { x: 7.8, y: 1.65, w: 4.8, h: 0.5, fontSize: 20, bold: true, color: '145C43', margin: 0 });
texto(s, '9 repositorios, uno por agregado. Los servicios llaman a métodos con significado de negocio y nunca escriben SQL.',
  { x: 7.1, y: 2.35, w: 5.4, h: 1.0, fontSize: 15 });
texto(s, '@Lock(LockModeType.PESSIMISTIC_WRITE)\n@Query("select b from Barbero b\n        where b.id in :ids order by b.id")\nList<Barbero> bloquearPorIds(\n        Collection<Long> ids);',
  { x: 7.1, y: 3.55, w: 5.4, h: 2.4, fontSize: 13, fontFace: 'Consolas', color: '14231D', fill: { color: 'FFFFFF' }, margin: 0.12 });
texto(s, 'BarberoRepository · bloqueo ② por barbero', { x: 7.1, y: 6.05, w: 5.4, h: 0.4, fontSize: 12, color: '4A5A54', italic: true });
s.addNotes('MVC: Spring Web MVC dirige cada petición a un controlador REST; el modelo son servicios, entidades y DTO; la vista es la representación JSON que Angular muestra. DAO: los repositorios de Spring Data ocultan el acceso a PostgreSQL; por ejemplo, bloquearPorIds toma el bloqueo pesimista por barbero.');

// 6 · SOLID y TDD
s = nueva('SOLID y TDD: lo que sí se aplicó', 'Diseño');
const solid = [
  ['S', 'Responsabilidad única', 'PoliticaTransiciones solo decide si un cambio de estado es válido; ReservaAutorizacion solo, quién puede ver.'],
  ['D', 'Inversión de dependencias', 'Todo se inyecta por constructor. El Clock inyectado permite probar reglas de fecha con un reloj fijo.'],
  ['I', 'Segregación de interfaces', 'Los servicios dependen de UsuarioActual (3 métodos), no del contexto completo de Spring Security.'],
  ['T', 'TDD documentado', `T-49 y T-58: prueba en rojo → código en verde. El resto: pruebas automatizadas, no TDD estricto.`],
];
solid.forEach(([l, t, d], i) => {
  const x = 0.6 + (i % 2) * 6.15, y = 1.45 + Math.floor(i / 2) * 2.7;
  tarjeta(s, x, y, 5.95, 2.45, i === 3 ? 'FFF7E6' : 'EEF5F1');
  insignia(s, x + 0.25, y + 0.25, l, i === 3 ? 'D9A441' : '145C43');
  texto(s, t, { x: x + 1.0, y: y + 0.28, w: 4.7, h: 0.5, fontSize: 19, bold: true, color: '14231D', margin: 0 });
  texto(s, d, { x: x + 0.25, y: y + 1.0, w: 5.45, h: 1.35, fontSize: 15 });
});
s.addNotes('SRP: PoliticaTransiciones es una función pura que también calcula los permisos que ve la interfaz. DIP: el reloj se inyecta; en producción es el real de Lima y en pruebas uno fijo. ISP: UsuarioActual con tres métodos. TDD: lo apliqué y documenté en T-49 y T-58; no lo atribuyo al proyecto entero, porque muchas pruebas no demuestran TDD.');

// 7 · Seguridad
s = nueva('Seguridad aplicada en el servidor', 'Diseño');
const seg = [
  ['1', 'Contraseñas', 'BCrypt coste 12; bloqueo de 15 min tras 5 intentos'],
  ['2', 'Sesión', 'JWT de 8 h en cookie HttpOnly, SameSite=Strict y Secure'],
  ['3', 'CSRF', 'Obligatorio en toda escritura, también con JWT en cookie'],
  ['4', 'Autorización', 'Rol por ruta y propiedad en el servicio: ajena → 404'],
  ['5', 'Cabeceras y HTTPS', 'HSTS, CSP, X-Frame-Options; HTTPS Only en Azure'],
  ['6', 'Registros', 'Sin contraseñas, tokens ni datos personales (ECS)'],
];
seg.forEach(([n, t, d], i) => {
  const x = 0.6 + (i % 2) * 6.15, y = 1.6 + Math.floor(i / 2) * 1.8;
  tarjeta(s, x - 0.1, y - 0.15, 5.95, 1.5, 'F4F7F5');
  insignia(s, x + 0.1, y + 0.1, n);
  texto(s, t, { x: x + 0.85, y, w: 4.9, h: 0.5, fontSize: 20, bold: true, color: '145C43', margin: 0 });
  texto(s, d, { x: x + 0.85, y: y + 0.55, w: 4.9, h: 0.7, fontSize: 16, margin: 0 });
});
s.addNotes('La interfaz puede ocultar botones, pero cada operación se autoriza en Java. Lo más delicado fue CSRF: Spring desactiva CSRF con JWT porque supone una cabecera, pero aquí el JWT viaja en cookie, así que se fuerza en toda escritura y cada endpoint tiene pruebas sin CSRF y con CSRF incorrecto. En T-49 además se estabilizó el token para Firefox.');

// 8 · Concurrencia
s = nueva('Dos clientes, la misma franja: solo una reserva', 'Diseño');
const capas = [
  ['Bloqueo por barbero', 'FOR NO KEY UPDATE y revalidación dentro de la transacción'],
  ['Red de seguridad en PostgreSQL', 'EXCLUDE USING gist: sin solapes por barbero ni por cliente'],
  ['Bloqueo optimista', '@Version: una acción sobre un estado viejo recibe 409'],
];
capas.forEach(([t, d], i) => {
  const y = 1.5 + i * 1.6;
  tarjeta(s, 0.6, y, 7.0, 1.4);
  insignia(s, 0.85, y + 0.42, String(i + 1));
  texto(s, t, { x: 1.6, y: y + 0.15, w: 5.8, h: 0.5, fontSize: 19, bold: true, color: '145C43', margin: 0 });
  texto(s, d, { x: 1.6, y: y + 0.65, w: 5.8, h: 0.65, fontSize: 15, margin: 0 });
});
tarjeta(s, 8.0, 1.5, 4.7, 4.6, '14231D');
texto(s, 'ConcurrenciaReservaIT', { x: 8.3, y: 1.75, w: 4.1, h: 0.45, fontSize: 16, bold: true, color: 'D9A441', margin: 0 });
texto(s, '10', { x: 8.3, y: 2.3, w: 4.1, h: 1.0, fontSize: 60, bold: true, color: 'FFFFFF', margin: 0 });
texto(s, 'solicitudes simultáneas a la misma franja', { x: 8.3, y: 3.3, w: 4.1, h: 0.5, fontSize: 14, color: 'EEF5F1', margin: 0 });
texto(s, '1 × 201 · 9 × 409', { x: 8.3, y: 4.0, w: 4.1, h: 0.8, fontSize: 32, bold: true, color: 'FFFFFF', margin: 0 });
texto(s, 'contra PostgreSQL real, en cada verify', { x: 8.3, y: 4.85, w: 4.1, h: 0.5, fontSize: 14, color: 'EEF5F1', margin: 0 });
texto(s, 'Orden fijo de bloqueos para evitar interbloqueos: ① cliente → ② barberos por id → ③ reserva', { x: 0.6, y: 6.35, w: 12.1, h: 0.45, fontSize: 14, italic: true, color: '4A5A54' });
s.addNotes('Consultar y luego guardar no basta: dos clientes pueden ver la misma franja libre. Por eso se bloquea la fila del barbero y después se revalida. Si un camino de código olvidara el protocolo, la restricción de exclusión de PostgreSQL lo impide. La prueba de concurrencia envía diez solicitudes a la vez: una gana y nueve reciben 409.');

// 9 · Bibliotecas
pres.addSection({ title: 'Recursos Java' });
s = nueva('Bibliotecas Java con uso real', 'Recursos Java');
T58.diapositivaBibliotecas.forEach(([n, t, d, sg], i) => {
  const x = 0.6 + i * 3.08;
  tarjeta(s, x, 1.45, 2.9, 5.2, i === 0 ? 'EEF5F1' : 'F4F7F5');
  insignia(s, x + 0.2, 1.65, n, i === 0 ? '145C43' : '4A5D9A');
  texto(s, t, { x: x + 0.2, y: 2.35, w: 2.5, h: 0.75, fontSize: 18, bold: true, color: '14231D', margin: 0 });
  texto(s, d, { x: x + 0.2, y: 3.1, w: 2.5, h: 2.2, fontSize: 14, margin: 0 });
  texto(s, sg, { x: x + 0.2, y: 5.35, w: 2.5, h: 1.15, fontSize: 12, italic: true, color: '4A5A54', margin: 0 });
});
s.addNotes(T58.notasBibliotecas);

// 10 · Interfaces
pres.addSection({ title: 'Producto' });
s = nueva('Interfaces funcionales para los tres roles', 'Producto');
imagen(s, img(CAP, 'Reservar360Paso1.png'), 0.6, 1.4, 2.4, 5.3, { marco: true, alt: 'Reservar en el teléfono' });
imagen(s, img(CAP, 'AgendaBarbero1440.png'), 3.25, 1.4, 4.6, 3.0, { marco: true, alt: 'Agenda del barbero' });
imagen(s, img(CAP, 'Reportes1440.png'), 8.1, 1.4, 4.6, 3.0, { marco: true, alt: 'Reportes del administrador' });
texto(s, 'Cliente · reservar (360 px)', { x: 0.6, y: 6.75, w: 2.4, h: 0.3, fontSize: 11, color: '4A5A54', margin: 0 });
texto(s, 'Barbero · agenda diaria', { x: 3.25, y: 4.45, w: 4.6, h: 0.3, fontSize: 11, color: '4A5A54', margin: 0 });
texto(s, 'Administrador · reportes', { x: 8.1, y: 4.45, w: 4.6, h: 0.3, fontSize: 11, color: '4A5A54', margin: 0 });
tarjeta(s, 3.25, 4.95, 9.45, 1.75);
texto(s, [
  { text: 'P01–P08 del APF2 implementadas: ', options: { bold: true } },
  { text: 'acceso y cuenta, reservar, mis citas, agenda, servicios, barberos, horarios, reportes y usuarios. Capturas de 360 y 1440 px con axe sin violaciones; E2E en Chromium y Firefox.' },
], { x: 3.5, y: 5.1, w: 9.0, h: 1.45, fontSize: 15, valign: 'middle' });
s.addNotes('Todas las pantallas comprometidas están implementadas y funcionan contra la API real. Las capturas salen del arnés de Playwright con datos ficticios; cada una pasó el análisis de accesibilidad sin violaciones.');

// 11 · Demostración
s = nueva('Demostración en vivo (4 minutos)', 'Producto');
const demo = [
  ['Cliente', 'Reserva Corte clásico con Carlos el 01/10; la ve en Mis citas con su aviso'],
  ['Cliente', 'Reprogramar: los botones vienen de los permisos que calcula el servidor'],
  ['Barbero', 'Agenda del día: solo sus citas y las acciones de atención permitidas'],
  ['Admin', 'Bloqueo que cruza una cita: el servidor lo rechaza (CP-07)'],
  ['Admin', 'Reportes de octubre → Descargar Excel; las cifras coinciden'],
];
demo.forEach(([r, d], i) => {
  const y = 1.45 + i * 1.02;
  insignia(s, 0.6, y + 0.12, String(i + 1));
  texto(s, r, { x: 1.35, y: y + 0.1, w: 1.6, h: 0.6, fontSize: 18, bold: true, color: '145C43', valign: 'middle', margin: 0 });
  texto(s, d, { x: 3.0, y: y + 0.1, w: 9.7, h: 0.6, fontSize: 17, valign: 'middle', margin: 0 });
});
texto(s, 'Entorno local con datos ficticios y reloj fijo del escenario del APF2 (28/09/2026 09:00). Si algo falla: capturas del informe y CI de GitHub.', { x: 0.6, y: 6.55, w: 12.1, h: 0.4, fontSize: 13, italic: true, color: '4A5A54' });
s.addNotes('Seguir la sección 4 del guion. Abrir tres ventanas: cliente, barbero y administrador. Si algo falla en vivo, mostrar las capturas del informe y explicar lo que debía ocurrir.');

// 12 · Git y GitHub
pres.addSection({ title: 'Evidencias' });
s = nueva('Git y GitHub: cada avance tiene su commit', 'Evidencias');
imagen(s, img(EV, 'github', 'gh01-repositorio.png'), 6.3, 1.4, 6.4, 5.4, { marco: true, alt: 'Repositorio público en GitHub' });
cifra(s, 0.6, 1.4, 2.7, `${T58.commitsMain}`, 'commits en main');
cifra(s, 3.3, 1.4, 2.7, `${T58.mergesMain}`, 'merges de tareas --no-ff');
texto(s, [
  { text: 'Una rama por tarea: tarea/T-XX', options: { bullet: true, breakLine: true } },
  { text: 'Commits convencionales con [T-XX]', options: { bullet: true, breakLine: true } },
  { text: 'CI en cada push: backend con PostgreSQL 18; frontend lint, Vitest y build', options: { bullet: true, breakLine: true } },
  { text: 'Hashes y notas de cierre en docs/tareas.md', options: { bullet: true } },
], { x: 0.6, y: 3.15, w: 5.4, h: 2.4, fontSize: 16, paraSpaceAfter: 8 });
texto(s, T58.diapositivaGit, { x: 0.6, y: 5.7, w: 5.4, h: 1.1, fontSize: 13, italic: true, color: '8A5A00' });
s.addNotes('Todo el proyecto está en el repositorio público. Cada tarea tiene su rama y se integra con merge sin avance rápido; los commits siguen la convención y citan la tarea. La CI se ejecuta en cada push. ' + T58.diapositivaGit);

// 13 · Pruebas y despliegue
s = nueva('Pruebas y despliegue en Azure', 'Evidencias');
cifra(s, 0.6, 1.4, 2.9, '1548', 'pruebas de backend (JUnit + PostgreSQL)');
cifra(s, 3.6, 1.4, 2.9, '442', 'pruebas de frontend (Vitest)');
cifra(s, 0.6, 3.15, 2.9, '48', 'casos E2E (Chromium y Firefox, 360 y 1440 px)');
cifra(s, 3.6, 3.15, 2.9, '✓', 'CI en verde: d2f3107 (39099141093) y 3bcd5d0 con T-58 (39716850704)');
texto(s, 'Checkpoint del 17–18/10/2026 sobre el código de d2f3107. ' + T58.diapositivaPruebas, { x: 0.6, y: 4.9, w: 5.9, h: 1.0, fontSize: 13, italic: true, color: '4A5A54' });
imagen(s, img(EV, 'azure', 'az01-ingresar-1440.png'), 6.85, 1.4, 5.85, 3.65, { marco: true, alt: 'Producción en Azure' });
texto(s, [
  { text: 'Azure App Service B1 · Java 21 · PostgreSQL 18.6 Flexible', options: { bold: true, breakLine: true } },
  { text: 'HTTPS Only, HSTS, health check y UptimeRobot cada 5 min. Versión desplegada: d2f3107.' },
], { x: 6.85, y: 5.2, w: 5.85, h: 1.3, fontSize: 14 });
s.addNotes('Los números son del checkpoint del 17 y 18 de octubre sobre el código publicado, con CI verde en GitHub. Está desplegado en Azure con HTTPS y monitor de salud. ' + T58.diapositivaPruebas);

// 14 · Pendientes
s = nueva('Lo que falta, declarado como pendiente', 'Evidencias');
const pend = [
  ['CP-11', 'Usabilidad con cinco personas: 0/5 sesiones'],
  ['RNF-02', '99 % mensual: se medirá noviembre completo'],
  ['PM-1…8', 'Prueba funcional mínima en producción'],
  ['RNF-09', 'Restauración ensayada en local; en Azure, pendiente'],
  ['Negocio', 'Caso académico: reglas por validar con una barbería real'],
  ['Java', '28,78 % del producto (41,56 % con pruebas) frente al 60 % estimado; informativo'],
];
pend.forEach(([k, d], i) => {
  const y = 1.45 + i * 0.84;
  tarjeta(s, 0.6, y, 12.1, 0.72, i % 2 ? 'FFFFFF' : 'F4F7F5');
  texto(s, k, { x: 0.85, y: y + 0.07, w: 2.0, h: 0.58, fontSize: 18, bold: true, color: '8A1F1F', valign: 'middle', margin: 0 });
  texto(s, d, { x: 3.0, y: y + 0.07, w: 9.5, h: 0.58, fontSize: 17, valign: 'middle', margin: 0 });
});
texto(s, 'Cobertura funcional implementada ≠ aceptación completa de los requisitos no funcionales.', { x: 0.6, y: 6.55, w: 12.1, h: 0.4, fontSize: 14, italic: true, color: '4A5A54' });
s.addNotes('Sobre Java: con el método del APF2 es 28,78 % del producto; la lógica sigue en Java y creció la interfaz. Prefiero declarar lo que falta: la usabilidad con personas, la disponibilidad de noviembre, la prueba funcional en producción y el ensayo de restauración en Azure. El negocio sigue siendo un caso académico.');

// 15 · Cierre
pres.addSection({ title: 'Cierre' });
s = nueva('Conclusiones', 'Cierre', 'CIERRE');
const concl = [
  'El diseño del APF2 se convirtió en un sistema que funciona para los tres roles',
  'MVC, DAO y SOLID con clases concretas; TDD declarado donde existe',
  'La integridad de las reservas está garantizada en el servidor y en la base',
  'Bibliotecas Java con función real y cuidado de la información',
  'Historial, CI y despliegue verificables en GitHub y Azure',
];
concl.forEach((t, i) => {
  insignia(s, 0.8, 1.6 + i * 0.95, String(i + 1), 'D9A441', 0.5);
  texto(s, t, { x: 1.55, y: 1.6 + i * 0.95, w: 11.0, h: 0.5, fontSize: 19, color: 'FFFFFF', valign: 'middle', margin: 0 });
});
texto(s, 'Gracias · github.com/sohnny-lima/BarberTurno', { x: 0.8, y: 6.45, w: 11.5, h: 0.45, fontSize: 16, bold: true, color: 'D9A441', margin: 0 });
s.addNotes('Cierre: el diseño se convirtió en un sistema funcionando; los patrones se ven en el código; la integridad de las reservas está en el servidor y la base; las bibliotecas tienen una función real; y todo es verificable en GitHub y Azure. Muchas gracias.');

const salida = process.argv[2];
pres.writeFile({ fileName: salida }).then(async () => { await applyTheme(salida, THEME); console.log('escrito', salida); });
