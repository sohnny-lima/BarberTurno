// Escribe t58.json con los datos verificados de T-58. Uso: node t58-datos.js
// Parámetros (variables de entorno): REF (commit entregable), CAPTURAS (carpeta chromium), PUBLICADO ("si"/"no"),
// GEMINI (texto del veredicto), EXCEL_PNG (captura del libro abierto en Excel, opcional).
const fs = require('fs');
const path = require('path');
const S = path.resolve(__dirname, '..');
const REF = process.env.REF || '[commit entregable]';
const PUBLICADO = process.env.PUBLICADO === 'si';
const GEMINI = process.env.GEMINI || 'revisión independiente de Gemini pendiente';
const CI = process.env.CI_TXT || 'CI pendiente';
const CAPT = process.env.CAPTURAS || path.join(S, 'apf3', 'evidencias', 'interfaces', 'chromium');
const T58EV = path.join(S, 'apf3', 'evidencias', 't-58');

const capturaExcel = [];
if (process.env.EXCEL_PNG) capturaExcel.push({ archivo: process.env.EXCEL_PNG, pie: 'Libro descargado abierto en Microsoft Excel: hoja «Resumen» con filtros, total y conteos por estado, servicio y profesional; sin fórmulas.', alto: 11 });

const estadoRama = PUBLICADO
  ? `integrada en \`main\` (merge \`${REF}\`) y publicada en GitHub (${CI})`
  : `en la rama \`tarea/T-58-exportar-excel\` (${REF}), todavía sin integrar en \`main\` ni publicar`;

const datos = {
  fechaInforme: process.env.FECHA || '21/10/2026',
  capturas: CAPT,
  listo: true,
  commitsMain: +(process.env.COMMITS || 436),
  mergesMain: +(process.env.MERGES || 54),
  ramasTarea: (process.env.RAMAS || '54') + ' ramas de tarea locales (su historia llega a GitHub mediante los merges de `main`)',
  tipos: JSON.parse(process.env.TIPOS || '{"feat":80,"fix":55,"test":55,"docs":218,"chore":17}'),
  resumenEstado: `T-58 está implementada con TDD documentado, probada (1578 pruebas de backend, 450 de frontend y 48 E2E) y revisada por el coordinador y por Gemini (${GEMINI}). Está ${estadoRama}; la versión desplegada en Azure sigue siendo \`d2f3107\`.`,
  notaVersion: `**Versión entregable y versión desplegada.** El código que acompaña este informe es el de ${PUBLICADO ? '`main`' : 'la rama `tarea/T-58-exportar-excel`'} en el commit \`${REF}\`${PUBLICADO ? ` (merge de T-58; ${CI})` : ''}, que incluye T-58.${PUBLICADO ? ' Los materiales de esta entrega se publicaron después, en commits solo de documentación registrados en `docs/cierre.md`.' : ''} La versión desplegada en Azure es \`d2f3107\`, **sin** la exportación a Excel: el responsable decidió no redesplegar por ahora, así que la demostración del Excel se hace en local. ${PUBLICADO ? '' : 'Integrar T-58 en `main`, publicarla en GitHub y redesplegar requieren la autorización del responsable.'}`,
  tddRojo: '`c95fd3e` generador: 12 de 12 fallan por aserción · `564e7b7` endpoint: 14 de 17 · `f3bb9b3` frontend: 8 nuevas fallan',
  tddVerde: '`27e6258` · `dc8c82c` · `9cfd521`',
  secciones: [
    {
      titulo: '7.2 Apache POI',
      parrafos: [
        'Apache POI es la biblioteca Java de referencia para leer y escribir documentos de Microsoft Office. En BarberTurno genera el archivo `.xlsx` del reporte que el administrador ya ve en pantalla (RF-14). `ReporteExcelService.generar` crea un `XSSFWorkbook` en memoria, escribe la hoja «Resumen» y la serializa a bytes; el controlador la entrega como descarga.',
      ],
      tabla: [
        ['Artefacto', '`org.apache.poi:poi-ooxml:5.5.1` (directa, versión verificada en Maven Central el 21/10/2026)'],
        ['Dónde', '`reporting/ReporteExcelService.java`, métodos `generar` y `fila`; endpoint `GET /api/reportes/resumen.xlsx` en `ReporteController.excel`'],
        ['Aporte', 'El administrador conserva o comparte el resumen fuera del sistema con las mismas cifras que la pantalla; los números se escriben como celdas numéricas para poder sumarlos en Excel'],
        ['Seguridad', 'Solo ADMIN; solo datos agregados (nombres de servicios y profesionales, sin clientes ni reservas individuales); nunca se escriben fórmulas (`setCellFormula` no se usa); _try-with-resources_ libera el libro; `Cache-Control: no-store`'],
      ],
      codigos: [{ titulo: 'reporting/ReporteExcelService.java, método generar (extracto)', texto: `try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
    var hoja = libro.createSheet("Resumen");
    var textoSeguro = libro.createCellStyle();
    textoSeguro.setQuotePrefixed(true);                 // estilo OOXML «texto», ver 7.3
    fila(hoja, textoSeguro, "Reporte de reservas · BarberTurno", null);
    fila(hoja, textoSeguro, "Fecha de generación (Lima)", FECHA.format(TiempoNegocio.aLima(reloj.instant())));
    fila(hoja, textoSeguro, "Total", resumen.total());
    for (var estado : EstadoReserva.values()) {
        fila(hoja, textoSeguro, etiqueta(estado), resumen.porEstado().getOrDefault(estado, 0L));
    }
    for (var grupo : resumen.porServicio()) fila(hoja, textoSeguro, grupo.nombre(), grupo.total());
    for (var grupo : resumen.porBarbero()) fila(hoja, textoSeguro, grupo.nombre(), grupo.total());
    libro.write(salida);
    return salida.toByteArray();
}

private void fila(Sheet hoja, CellStyle textoSeguro, String etiqueta, Object valor) {
    var fila = hoja.createRow(hoja.getPhysicalNumberOfRows());
    texto(fila.createCell(0), etiqueta, textoSeguro);
    if (valor instanceof Long numero) fila.createCell(1).setCellValue(numero.doubleValue());   // celda numérica
    else if (valor instanceof String cadena) texto(fila.createCell(1), cadena, textoSeguro);
}` }],
      despues: ['La prueba `ReporteExcelServiceTest.contenidoCompletoNumericoSinFormulasNiHojasOcultas` vuelve a abrir el libro con POI y comprueba el contenido, que el total es numérico y que **ninguna** celda es de tipo fórmula. `ReporteExcelIT.adminDescargaMismasCifrasQueJsonConYSinFiltros` descarga el archivo por HTTP contra PostgreSQL y compara cada cifra con `GET /api/reportes/resumen` para los mismos filtros.'],
    },
    {
      titulo: '7.3 Apache Commons (Commons Lang)',
      parrafos: [
        'Apache Commons es una familia de bibliotecas; se usa **Commons Lang** (`commons-lang3`), que amplía las utilidades de `java.lang`. Antes de T-58 solo llegaba como dependencia transitiva y no se usaba; ahora se declara de forma directa (con la versión que gestiona Spring Boot) y cumple una función de seguridad concreta.',
        '**Problema que resuelve: inyección de fórmulas.** Si un texto empieza por `=`, `+`, `-` o `@` (o por tabulación o retorno de carro), una hoja de cálculo puede interpretarlo como fórmula cuando el usuario edita la celda; OWASP lo describe como _CSV/Formula Injection_. Los nombres de servicios y profesionales los escribe un administrador, pero el archivo puede reenviarse. `StringUtils.startsWithAny` detecta esos prefijos y la celda recibe el estilo `quotePrefixed` de OOXML, que obliga a Excel a tratarla como texto. **El valor no se modifica**: no se añade un apóstrofo ni se borran caracteres.',
      ],
      tabla: [
        ['Artefacto', '`org.apache.commons:commons-lang3:3.20.0` (directa; versión gestionada por Spring Boot 4.1.1)'],
        ['Dónde', '`ReporteExcelService.texto`'],
        ['Aporte', 'Una sola expresión, legible y probada, para la lista de prefijos peligrosos'],
        ['Seguridad', 'Defensa en profundidad para la información exportada; la prueba parametrizada cubre los seis prefijos'],
      ],
      codigos: [
        { titulo: 'reporting/ReporteExcelService.java, método texto', texto: `private void texto(Cell celda, String valor, CellStyle textoSeguro) {
    celda.setCellValue(valor);                         // siempre celda de texto, valor intacto
    if (StringUtils.startsWithAny(valor, "=", "+", "-", "@", "\\t", "\\r")) celda.setCellStyle(textoSeguro);
}` },
        { titulo: 'ReporteExcelServiceTest.java: prueba parametrizada', texto: `@ParameterizedTest @ValueSource(strings = {"=SUM(A1:A2)", "+Corte", "-Corte", "@Corte", "\\tCorte", "\\rCorte"})
void conservaTextoDeRiesgoYActivaQuotePrefixed(String nombre) throws Exception {
    // ...
    assertThat(celda.getCellType()).isEqualTo(CellType.STRING);
    assertThat(celda.getStringCellValue()).isEqualTo(nombre);          // el dato no cambia
    assertThat(celda.getCellStyle().getQuotePrefixed()).isTrue();      // pero se trata como texto
}` },
      ],
    },
    {
      titulo: '7.4 Google Guava',
      parrafos: [
        'Guava es la biblioteca de utilidades de Google para Java. En T-58 cumple dos funciones: **verificar una invariante del negocio antes de entregar el archivo** y **medir la generación** para el registro técnico.',
        '`Preconditions.checkState` comprueba la regla de RF-14: la suma por estado, la suma por servicio y la suma por profesional deben coincidir con el total. Si no coinciden, no se genera un archivo incoherente: se registra una advertencia sin datos y la API responde 500 genérico, sin archivo. `Stopwatch` mide el tiempo con un `Ticker` inyectable, de modo que la prueba usa un reloj monotónico simulado (125 ms exactos) en lugar de depender del tiempo real.',
      ],
      tabla: [
        ['Artefacto', '`com.google.guava:guava:33.7.2-jre` (directa; publicada el 29/09/2026, verificada en Maven Central el 21/10/2026)'],
        ['Dónde', '`ReporteExcelService.generar` (`Preconditions`, `Stopwatch`) y su constructor (`Ticker`)'],
        ['Aporte', 'Integridad del archivo exportado y medición testeable de una operación más costosa que el JSON'],
        ['Seguridad', 'Evita entregar información inconsistente; la duración se registra sin datos personales'],
      ],
      codigos: [{ titulo: 'reporting/ReporteExcelService.java (extracto)', texto: `Preconditions.checkState(
        resumen.porEstado().values().stream().mapToLong(Long::longValue).sum() == resumen.total()
        && resumen.porServicio().stream().mapToLong(ConteoReporteDto::total).sum() == resumen.total()
        && resumen.porBarbero().stream().mapToLong(ConteoReporteDto::total).sum() == resumen.total(),
        "Las agrupaciones del reporte no concilian.");
var cronometro = Stopwatch.createStarted(ticker);        // Ticker inyectable: real en producción, simulado en pruebas
// ... generación del libro
LOG.info("Reporte Excel generado: actor={} filas={} bytes={} duracionMs={}", actorId,
        hoja.getPhysicalNumberOfRows(), archivo.length, cronometro.stop().elapsed(TimeUnit.MILLISECONDS));` }],
      despues: ['Esta misma línea de registro es la aplicación de **Logback** en T-58: `ReporteExcelServiceTest.infoSoloIncluyeActorFilasBytesYDuracionMonotonica` captura el evento con `ListAppender` y comprueba que el mensaje es exactamente `actor=7 filas=18 bytes=… duracionMs=125`, aunque el resumen contenga «=Nombre privado» y un correo: ningún dato personal llega al registro.'],
    },
  ],
  resumenBibliotecas: [
    ['Logback', 'logback-classic 1.5.38', 'Transitiva (Boot)', '`AuthService.login`, `ManejadorErrores`, `ReporteExcelService.generar`', 'Registro estructurado (ECS en producción)', 'Sin contraseñas, tokens, correos ni nombres; probado con `ListAppender`'],
    ['Apache POI', 'poi-ooxml 5.5.1', 'Directa (T-58)', '`ReporteExcelService.generar`, `fila`', 'Archivo .xlsx del reporte RF-14', 'Solo ADMIN, solo agregados, sin fórmulas'],
    ['Apache Commons Lang', 'commons-lang3 3.20.0', 'Directa (T-58)', '`ReporteExcelService.texto`', 'Detección de textos con riesgo de fórmula', '`quotePrefixed` sin alterar el dato'],
    ['Google Guava', 'guava 33.7.2-jre', 'Directa (T-58)', '`ReporteExcelService.generar`', 'Invariante RF-14 y medición', 'No entrega archivos incoherentes'],
  ],
  notaCapturas: 'Las capturas de cliente, barbero y administración proceden del arnés `frontend/e2e/capturas.spec.ts` sobre la base aislada `barberturno_test` con datos ficticios (`@ejemplo.test`). ' + (process.env.NOTA_CAPTURAS || 'Las de Reservar, Mis citas y Agenda se tomaron en T-55 (08/10/2026, `fd628ba`) y las de acceso y administración en T-57 (18/10/2026, `cb3d756`); la de Reportes con el botón de Excel, en T-58 (21/10/2026).'),
  capturaExcel,
  pruebasRf14: 'ReporteExcelServiceTest (12), ReporteExcelIT (18)',
  notaGit: PUBLICADO
    ? `**Publicado en GitHub (21/10/2026, autorizado por el responsable):** el código de T-58 integrado en \`main\` (merge \`${REF}\`; ${CI}); los materiales del APF3 en \`entregables/apf3/\` (sin los ZIP, que se entregan en el aula); y el informe final del APF2, sin modificar, en \`entregables/apf2-final/\`. Los materiales se publicaron en un commit de documentación posterior al del código, registrado en \`docs/cierre.md\`. El borrador del APF2 sigue en \`docs/apf2/\` como referencia de solo lectura. Las capturas de GitHub de este informe se tomaron después de publicar el código.`
    : `**Publicado en GitHub:** todo el código y la documentación hasta \`d3ca8bc\` (19/10/2026), incluidos el informe y los materiales del APF2 en \`docs/apf2/\` (versión de trabajo) y el Javadoc de la entrega en \`entregables/apf2-final/javadoc/\`. **Solo en local, a la fecha de este informe:** la rama de T-58 (${REF}), la versión final corregida del informe del APF2 y los entregables del APF3. Publicarlos requiere la autorización del responsable; mientras no se publiquen, el criterio 3 no puede acreditar el 100 % de los entregables en GitHub.`,
  filasPruebas: [
    ['T-58 · backend', '`mvnw clean verify`', '**1578/1578** (+30: 12 del generador y 18 del endpoint); JaCoCo `reporting` 100 %; Javadoc sin avisos', `Codex, 21/10/2026, rama de T-58 (\`8053eaf\`); el coordinador repitió las 60 pruebas de reportes`],
    ['T-58 · frontend', 'Vitest, Angular CLI', '**450/450** en Lima y Madrid; build inicial **496,42 kB** (+0,03 kB)', 'Codex, 21/10/2026'],
    ['T-58 · E2E', 'Playwright', '**48/48** con la descarga en Chromium y Firefox', 'Codex, 21/10/2026'],
    ['T-58 · TDD', 'Commit rojo en un worktree', '`c95fd3e`: 12 de 12 fallan por aserción', 'Repetido por el coordinador, 21/10/2026'],
  ],
  medicionJava: 'Medición del 21/10/2026 sobre la rama de T-58 (`node tools/medir-java.mjs`): Java es el **28,78 %** de las líneas de producto y el **41,56 %** contando pruebas. La proporción bajó respecto del 05/10/2026 (40,25 % y 55,02 %) porque la interfaz creció con las fases visuales T-48, T-55 y T-57 (plantillas, estilos y pruebas de Angular). Desde el 04/10/2026 el responsable la reclasificó como métrica informativa (RA-02): toda la lógica de negocio sigue en Java. GitHub, que mide bytes y no líneas, muestra Java 49,7 %.',
  notaDespliegue: `**Diferencia con la entrega académica.** Azure ejecuta el jar de \`d2f3107\` (19/10/2026); la versión entregada es \`${REF}\`, que añade solo T-58 (exportación a Excel y sus dependencias POI, Commons Lang y Guava). Por decisión del responsable no se redespliega por ahora: en producción no aparece el botón «Descargar Excel» y la demostración de T-58 se hace en local. Un redespliegue futuro requiere su autorización y repetir las comprobaciones de salud y cabeceras del registro de despliegue. Siguen pendientes CP-11, la medición de disponibilidad (RNF-02) y las pruebas operativas en producción.`,
  matrizResumen: [
    ['1. Diseño de la solución', '3', 'MVC, DAO y SOLID con clases concretas; TDD en T-49 y T-58; seguridad en el servidor', 'Ninguna esencial; TDD declarado como parcial'],
    ['2. Recursos Java', '2', 'Logback (registro sin datos personales) y POI, Commons Lang y Guava en T-58, con pruebas', PUBLICADO ? 'Ninguna esencial; producción sin T-58' : 'T-58 sin integrar ni publicar; producción sin T-58'],
    ['3. Control de versiones', '3', `${436} commits en \`main\`, merges por tarea, CI, capturas auténticas`, PUBLICADO ? 'Ninguna esencial' : 'Entregables del APF3, T-58 y APF2 final aún no publicados en GitHub'],
    ['4. Interfaces gráficas', '6', 'P01–P08 implementadas para los tres roles; E2E 48/48; capturas con axe 0', 'Ninguna esencial para la cobertura; CP-11 (usabilidad) pendiente'],
    ['5. Construcción del producto', '4', 'Completa, coherente, buenas prácticas; uso de IA declarado', 'El dominio del código debe demostrarse en la sustentación'],
    ['6. Sustentación oral', '2', 'Presentación, guion y preguntas de defensa', 'Depende del ensayo y la exposición'],
  ],
  diapositivaBibliotecas: [
    ['L', 'Logback', 'Backend de SLF4J. Formato ECS en producción. Registra cada exportación sin datos personales.', 'Probado con ListAppender: ni correos ni nombres.'],
    ['P', 'Apache POI', 'Genera el .xlsx del reporte: hoja, celdas de texto y celdas numéricas. Nunca fórmulas.', 'Solo ADMIN y solo datos agregados.'],
    ['C', 'Commons Lang', 'StringUtils.startsWithAny detecta =, +, -, @ al inicio del texto.', 'Estilo quotePrefixed: Excel lo trata como texto sin cambiar el dato.'],
    ['G', 'Google Guava', 'Preconditions.checkState exige que los totales cuadren; Stopwatch mide la generación.', 'No se entrega un archivo incoherente.'],
  ],
  notasBibliotecas: 'Logback ya registraba los eventos del sistema sin datos personales. Para usar de verdad las otras tres, añadí en T-58 la exportación a Excel del reporte: POI genera el archivo; Commons Lang detecta los textos que Excel podría tomar como fórmula y les aplica un estilo que los fuerza a texto sin cambiar el dato; Guava comprueba que los totales cuadren antes de entregar el archivo y mide el tiempo; y Logback registra la exportación sin datos personales. Lo hice con TDD: primero las pruebas en rojo y luego el código. ' + (PUBLICADO ? '' : 'Todavía no está desplegado en Azure.'),
  diapositivaGit: PUBLICADO ? `Código de T-58 en main (merge ${REF}, ${CI}); materiales del APF3 y APF2 final publicados.` : `Pendiente: publicar en GitHub la rama de T-58 (${REF}) y los entregables (requiere autorización).`,
  diapositivaPruebas: 'T-58 (en main desde 3bcd5d0, 21/10/2026): 1578/1578 backend, 450/450 frontend, 48/48 E2E.',
};
fs.writeFileSync(path.join(__dirname, 't58.json'), JSON.stringify(datos, null, 2));
console.log('t58.json escrito', REF, PUBLICADO ? 'publicado' : 'local');
