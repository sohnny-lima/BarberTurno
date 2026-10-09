# Integración en `main` de T-54, T-55 y T-56

Fecha: 09/10/2026 (America/Lima). Coordinador: Claude Code. Autorización del responsable: integrar la rama final, hacer push y esperar la CI.

## Qué se integró

- **Merge:** `17e4081efc35959983802f8b5fe5026b60657938` (`--no-ff`). Primer padre: `main` `99d6da4`. Segundo padre: `tarea/T-56-ayuda-password` `8eee7c4`.
- **Contenido:** T-56 (`8eee7c4`) contiene T-55 (`c5d3c0c`), que contiene T-54 (`daa3e34`). T-54 reúne T-52 (T-48 + T-51) y T-49 (CSRF y avisos del barbero). Por eso no se fusionaron por separado las ramas anteriores; se conservan con su historial.
- **Árbol:** idéntico al de la punta de T-56 (`git diff tarea/T-56-ayuda-password main` vacío).
- **Diferencia con `origin/main` (`db5659a`):** 89 commits. Incluye los cambios de backend de T-49 (`AuthController`, `SecurityConfig`, `AuthCsrfHttpIT`, `AuthIT`, `AuthCookieSecureIT`).
- **Aprobación de T-55:** el responsable aprobó la dirección verde basándose en la propuesta y las evidencias entregadas (09/10/2026). **No realizó una revisión manual completa** de la aplicación.
- **Fase 2:** queda aplazada fuera de este cierre.

## Comprobaciones locales sobre `17e4081`

Se ejecutaron en orden, una suite cada vez. Node 24.21.0 y JDK 21.0.8.

| Paso | Resultado |
|---|---|
| `mvnw verify` (1.ª ejecución, sin `clean`) | **rc=1**: 1548 pruebas, 6 errores en `AuthCookieSecureIT` (2) y `AuthCsrfHttpIT` (4); 578 s. Ver observación O-1. |
| `npm run lint` | OK |
| `npm run format:check` | OK |
| Vitest, `TZ=America/Lima` | 41 archivos, 392/392; 39,5 s (entorno 88 %) |
| Vitest, `TZ=Europe/Madrid` | 41 archivos, 392/392; 19,2 s |
| `npm run build` | OK; inicial 496,39 kB, sin avisos; presupuesto de 500 kB sin cambios |
| E2E (`npm run e2e`, `barberturno_test`) | 48/48 en Chromium y Firefox, a 1440 y 360 px; 7,6 min |
| `mvnw test -Dtest=AuthCookieSecureIT,AuthCsrfHttpIT` | 6/6 |
| `mvnw clean verify` | **1548/1548**, cobertura cumplida, Javadoc sin avisos, `BUILD SUCCESS`; 513 s |

No se ejecutó `e2e:capturas`. Esa suite regenera las capturas versionadas de T-55, y su código no cambia desde el checkpoint `6507f0f` (36/36).

## GitHub Actions

- **Ejecución:** [37895739087](https://github.com/sohnny-lima/BarberTurno/actions/runs/37895739087), push de `17e4081`, conclusión **success**.
- **backend:** `mvnw verify` con PostgreSQL 18; success, de 06:51:27 a 06:58:01 UTC.
- **frontend:** lint, formato, pruebas y compilación; success, de 06:51:29 a 06:52:26 UTC.
- **Anotación (solo aviso):** «The ubuntu-latest label will migrate to Ubuntu 26 beginning October 19, 2026». No requiere cambios ahora; conviene vigilar la primera ejecución después de esa fecha.

## Observaciones abiertas (causa sin confirmar)

### O-1 · Errores de contexto en la primera ejecución local de `mvnw verify`

**Errores observados:**
- `AuthCookieSecureIT`: no se cargó el contexto (*Failed to determine a suitable driver class*). El perfil `test` estaba activo, pero el arranque no muestra el nombre de la aplicación. Esto indica que `application.yml` y `application-test.yml` no estaban en el *classpath* en ese momento.
- `AuthCsrfHttpIT`: no se encontró el bean `RelojAjustable`, aunque la clase lo importa con `@Import(RelojAjustable.Configuracion.class)`.
- Las 54 clases restantes pasaron.

**Intentos de reproducción:** el fallo no se reprodujo ni con las dos clases aisladas ni con `clean verify` completo. En la CI tampoco aparece. El mismo backend pasó 1548/1548 en T-54 (`docs/pruebas/t-54.md`).

**Causa probable (no confirmada):** la extensión Java de VS Code (JDT LS, activa desde el 07/10) compila el proyecto `barberturno` en `backend/target/classes` y `backend/target/test-classes`, las mismas carpetas que usa Maven. Así consta en su `.classpath` interno. El `verify` empezó unos dos minutos después del cambio de rama a `main`, con 89 commits nuevos. Una reconstrucción simultánea del IDE explicaría los dos síntomas.

**Por qué no se confirma:**
- No hay registro de la actividad de JDT LS en ese intervalo.
- No hay huellas de m2e en `target/`.

**No se considera resuelto por pasar al repetirlo.**

**Mitigación propuesta, sin aplicar:** al ejecutar `verify` localmente después de cambiar de rama, usar `clean verify` y esperar a que el IDE termine de compilar. Separar la salida del IDE sería un cambio de configuración y se propondrá al responsable.

### O-2 · Timeouts de Vitest en Lima

**Dónde aparecieron:**
- **Encargo 067 (T-56, Codex):** dos ejecuciones iniciales en Lima fallaron por *Test timed out in 5000ms*.
  - Primera: 391/392.
  - Segunda: 389/392.
  - Casos afectados: `app.routes.spec.ts` («protege /perfil…», «un cliente no puede abrir /admin/servicios») y `horarios.spec.ts`.
  - Duraciones de 38 y 55 s, frente a unos 20 s habituales.
- **Sin cambios:** la tercera ejecución y las del coordinador pasaron 392/392, sin tocar pruebas, plazos ni configuración. En esta integración, Lima tardó 39,5 s, con el 88 % del tiempo en preparar el entorno, y pasó.

**Contexto:** coinciden con carga de la máquina (aplicación de revisión, navegadores y JDT LS abiertos). En la CI, las pruebas del frontend tardan unos 16 s y pasan.

**Causa: sin confirmar.** No se han subido plazos ni añadido reintentos.

## Commit de documentación

Este registro y el estado de T-48, T-49, T-51 y T-55 en `docs/tareas.md` van en un único commit `docs` posterior al merge (AGENTS.md §6, Git). Su propia ejecución de CI se comunica en el informe al responsable, para que el documento no tenga que citarse a sí mismo.
