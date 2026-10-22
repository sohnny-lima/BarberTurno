// Rellena los marcadores {{...}} de Matriz_Rubrica.md y README.md. Uso: node rellenar.js '<json de páginas>'
const fs = require('fs');
const path = require('path');
const A = path.resolve(__dirname, '..', 'apf3');
const pag = JSON.parse(process.argv[2]).secciones;
const REF = process.env.REF || 'e0ebc54';
const PUBLICADO = process.env.PUBLICADO === 'si';
const comun = {
  FECHA: '21/10/2026',
  COMMIT_REF: PUBLICADO ? `\`${REF}\` en \`main\`` : `\`${REF}\` (rama \`tarea/T-58-exportar-excel\`, solo en local; incluye T-58)`,
  COMMITS: process.env.COMMITS || '436', MERGES: process.env.MERGES || '54',
  P3: pag[3], P4: pag[4], P5: pag[5], P6: pag[6], P7: pag[7], P8: pag[8], P9: pag[9], P10: pag[10], P11: pag[11], P14: pag[14], P15: pag[15],
};
const matriz = {
  ...comun,
  TDD_T58: '; T-58 `c95fd3e`→`27e6258`, `564e7b7`→`dc8c82c`, `f3bb9b3`→`9cfd521` (rojo de `c95fd3e` repetido por el coordinador: 12/12 fallan)',
  C2_EVIDENCIA: '**Logback 1.5.38** (transitiva de Boot): registros SLF4J sin datos personales, ECS en producción, pruebas con `ListAppender`. **Apache POI 5.5.1**: genera el `.xlsx` del reporte RF-14 sin fórmulas. **Commons Lang 3.20.0**: `StringUtils.startsWithAny` + `quotePrefixed` contra la inyección de fórmulas sin alterar el dato. **Guava 33.7.2-jre**: `Preconditions.checkState` (invariante RF-14) y `Stopwatch` con `Ticker` inyectable. Seguridad: solo ADMIN, solo agregados, `no-store`, errores `problem+json`',
  C2_ARCHIVOS: '`backend/pom.xml`; `reporting/ReporteExcelService.java`, `ReporteService.exportar`, `ReporteController.excel`; `ReporteExcelServiceTest` (12), `ReporteExcelIT` (18); `ManejadorErroresTest`; `application-prod.yml`; `evidencias/t-58/` (`reporte-ejemplo.xlsx`, vista en Excel); T-58 aprobada por el coordinador y Gemini g037',
  C2_BRECHA: PUBLICADO ? 'Producción (Azure) sin T-58 hasta un redespliegue autorizado' : '**T-58 no está integrada en `main` ni publicada**: si el docente consulta GitHub no la verá. Producción (Azure) sin T-58',
  C3_BRECHA: PUBLICADO ? 'Ninguna esencial' : '**Esencial para el nivel máximo:** los entregables del APF3, la rama de T-58 y el informe final corregido del APF2 siguen solo en local. Sin publicarlos, la evidencia en GitHub no alcanza el 100 % de los entregables',
  C4_BRECHA: 'Ninguna en cobertura funcional. CP-11 (usabilidad con cinco personas) es aceptación no funcional y sigue pendiente',
  BRECHAS: [
    PUBLICADO ? null : '1. **Integrar T-58 en `main` y publicar (decisión del responsable).** Afecta a los criterios 2 y 3. Requiere su revisión del cambio, `merge --no-ff`, push y CI en verde.',
    PUBLICADO ? null : '2. **Publicar en GitHub los entregables (decisión del responsable).** `entregables/apf3/` (sin el ZIP) y el informe final corregido del APF2 (hoy solo en `D:\\copia descargas\\`; el de `docs/apf2/` es un borrador del 28/09). Sin esto, el criterio 3 no acredita el 100 %.',
    '3. **Redesplegar en Azure con T-58 (opcional, decisión del responsable).** Si no se hace, la demostración del Excel será local y producción seguirá en `d2f3107`.',
    '4. **Ensayar la sustentación** con `Guion_Sustentacion.md` y la demostración local; el criterio 5 (autoría o dominio) y el 6 dependen de explicar el código con soltura.',
    '5. **Evidencia no funcional pendiente** (no la exige esta rúbrica, pero se declara): CP-11 0/5, RNF-02 (noviembre), prueba mínima en producción (PM-1…PM-8) y ensayo de restauración en Azure.',
    '6. **Participación de Java (RA-02, informativa):** 28,78 % del código de producto y 41,56 % con pruebas frente al 60 % estimado en el APF2. Se mide con el método comprometido y no se maquilla; la lógica de negocio sigue en Java. Si el docente evalúa el porcentaje, defenderlo con el informe §11.1.',
  ].filter(Boolean).join('\n'),
};
const readme = {
  ...comun,
  T58_EVIDENCIAS: 'Exportación a Excel: capturas, `t-58.md` (TDD y verificación), `reporte-ejemplo.xlsx` con datos ficticios y su vista en Excel',
  CODIGO: `\`BarberTurno-${REF}.zip\`: exportación limpia (\`git archive\`) del commit \`${REF}\`; contiene solo archivos versionados (sin \`.local/\`, \`node_modules/\` ni \`target/\`)`,
  PENDIENTES: [
    PUBLICADO ? null : `- T-58 (exportación a Excel) está en la rama \`tarea/T-58-exportar-excel\` (\`${REF}\`), revisada y aprobada, **sin integrar en \`main\` ni publicar**.`,
    PUBLICADO ? null : '- Los entregables del APF3 y el informe final del APF2 no están publicados en GitHub.',
    '- Azure ejecuta `d2f3107` (sin T-58).',
    '- CP-11: 0/5 sesiones de usabilidad. RNF-02: medición de noviembre de 2026. Prueba mínima en producción (PM-1…PM-8) y ensayo de restauración en Azure sin ejecutar.',
    '- Participación de Java: 28,78 % del producto (41,56 % con pruebas) frente al 60 % estimado en el APF2; métrica informativa (RA-02), explicada en el informe §11.1.',
    '- Validación de las reglas con una barbería real (caso académico).',
  ].filter(Boolean).join('\n'),
};
function rellenar(archivo, mapa) {
  let s = fs.readFileSync(path.join(__dirname, archivo.replace('.md', '.plantilla.md.bak')), 'utf8');
  s = s.replace(/\{\{([A-Z0-9_]+)\}\}/g, (m, k) => (k in mapa ? String(mapa[k]) : m));
  const resto = s.match(/\{\{[A-Z0-9_]+\}\}/g);
  if (resto) throw new Error(`${archivo}: marcadores sin valor ${resto.join(', ')}`);
  fs.writeFileSync(path.join(A, archivo), s);
  console.log('ok', archivo);
}
rellenar('Matriz_Rubrica.md', matriz);
rellenar('README.md', readme);
