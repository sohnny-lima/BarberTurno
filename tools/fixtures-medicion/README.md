# Fixtures de medición

Árbol artificial con las mismas raíces que el producto. No se compila ni forma parte de las LOC reales. `esperado.json` fija conteos calculados manualmente, clasificación, exclusiones y porcentajes; no se genera a partir del medidor.

| Archivo contado | LOC | Clasificación | Casos |
|---|---:|---|---|
| backend/src/main/Casos.java | 14 | Producto | Javadoc, comentarios mixtos/multilínea, escapes, carácter, URL, /** literal, text block y delimitador escapado |
| backend/src/main/esquema.sql | 4 | Producto | -- en cadena, comillas duplicadas, identificador entre comillas y bloque multilínea |
| backend/src/main/MarcaSexta.java | 1 | Producto | Marca en sexta línea: no excluye |
| backend/src/test/CasosTest.java | 3 | Pruebas | Clasificación backend |
| frontend/src/casos.spec.ts | 1 | Pruebas | Clasificación spec |
| frontend/src/casos.ts | 8 | Producto | URL, /** literal, escape, plantilla multilínea e interpolación opaca |
| frontend/src/estilos.css | 5 | Producto | // cuenta en CSS; cadenas y bloque multilínea |
| frontend/src/estilos.scss | 5 | Producto | // no cuenta en SCSS; cadenas, escape y bloque multilínea |
| frontend/src/pagina.html | 5 | Producto | Bloques multilínea, atributo con marcador y apóstrofo en texto |
| frontend/src/solo-cadena.ts | 2 | Producto | Líneas cuyo único código es una cadena con marcadores |
| frontend/src/vacio.ts | 0 | Producto | Solo blancos y comentarios |
| frontend/e2e/reserva.ts | 2 | Pruebas | Clasificación E2E |
| perf/Carga.java | 1 | Carga | Código fuera de perf/src |
| perf/src/Carga.java | 2 | Carga | Código dentro de perf/src |

Totales: producto 44, pruebas 9 (carga 3 incluida), total 53. Java 21/53 con pruebas y 15/44 sin pruebas. Se verifican también siete exclusiones: las dos marcas inglesas/españolas (incluida la quinta línea), la paleta explícita sin marca, otro generado, JSON, YAML y el directorio docs.

`node tools/medir-java.mjs --verificar-fixtures` compara todo el resultado con `esperado.json`. `node --test tools/verificar-medicion.test.mjs` comprueba además fallo ante una discrepancia, CLI desde otro cwd, JSON/Markdown, escritura incremental, denominador cero, CRLF/EOF, carpetas excluidas y errores de opciones. Usa árboles temporales bajo tools y los retira comprobando primero sus rutas absolutas; no versiona dependencias ni salidas de build.
