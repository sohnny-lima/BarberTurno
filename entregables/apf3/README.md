# BarberTurno · Entrega del APF3

**Actividad:** (AC-S12-APF3) Avance de Proyecto Final 3 · rúbrica SI53-APF3-2026A · Curso Integrador I: Sistemas Software, sección 20058 (UTP).
**Estudiante:** Sohnny Walter Lima Infanzón · **Docente:** Mg. Ing. Juan Barahona Sánchez.
**Fecha límite:** 29/10/2026, 22:30 (hora de Lima). **Preparado el:** 21/10/2026.

## Contenido
| Archivo o carpeta | Qué es |
|---|---|
| `APF3_BarberTurno_Informe.pdf` | Informe para leer y evaluar |
| `APF3_BarberTurno_Informe.docx` | Informe editable (mismo contenido que el PDF) |
| `APF3_BarberTurno_Sustentacion.pptx` | Presentación de la sustentación, con notas del orador |
| `Guion_Sustentacion.md` | Guion por bloques, demostración y preguntas de defensa |
| `Matriz_Rubrica.md` | Criterio → evidencia → archivo → sección del informe → brecha |
| `evidencias/github/` | Capturas auténticas del repositorio, del historial, de commits y de GitHub Actions (21/10/2026) |
| `evidencias/interfaces/` | Capturas de las pantallas por rol a 360 y 1440 px (arnés Playwright con datos ficticios) |
| `evidencias/azure/` | Capturas del despliegue (GET públicos, sin sesión) y cabeceras observadas |
| `evidencias/diagramas/` | Arquitectura, modelo de datos y secuencia de una reserva (PNG) |
| `evidencias/t-58/` | Exportación a Excel: capturas, `t-58.md` (TDD y verificación), `reporte-ejemplo.xlsx` con datos ficticios y su vista en Excel |
| `fuentes/diagramas/` | Diagramas editables (SVG) |
| `fuentes/generadores/` | Scripts Node que generan el informe y la presentación |
| `codigo/` | `BarberTurno-3bcd5d0.zip`: exportación limpia (`git archive`) del commit `3bcd5d0`; contiene solo archivos versionados (sin `.local/`, `node_modules/` ni `target/`) |

## Versiones de referencia
| Elemento | Valor |
|---|---|
| Repositorio | https://github.com/sohnny-lima/BarberTurno |
| Código entregado (APF3) | `3bcd5d0` en `main` |
| Código publicado en `main` y desplegado en Azure | `d2f3107` (CI 39099141093 success) |
| Producción | https://barberturno-web-202610-bvbacecef3hpcga6.westus-01.azurewebsites.net |

## Ejecutar el sistema
Requisitos: JDK 21, Node 24.21.0 (fnm, `.node-version`), PostgreSQL 18 en el puerto 5433 y Git. No se necesita Docker.
1. Preparar la base local: `psql -X -h localhost -p 5433 -U postgres -d postgres -f tools/db-local.sql` (crea el rol `barberturno` y las bases `barberturno` y `barberturno_test`; pide la contraseña sin mostrarla).
2. Definir las variables de entorno (ver `tools/barberturno.env.example`; nunca versionar valores reales).
3. Backend y pruebas: `cd backend` y `./mvnw verify` (Windows: `mvnw.cmd verify`).
4. Frontend: `cd frontend`, `npm ci`, `npm run lint`, `npm test -- --watch=false`, `npm run build`.
5. Demostración con datos ficticios: definir `BT_DEMO_PASSWORD` y ejecutar `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo -Dspring-boot.run.arguments=--barberturno.reloj-fijo=2026-09-28T09:00:00-05:00`. Detalle en el `README.md` del repositorio y en `Guion_Sustentacion.md` §4.

## Pendientes declarados
- Azure ejecuta `d2f3107` (sin T-58).
- CP-11: 0/5 sesiones de usabilidad. RNF-02: medición de noviembre de 2026. Prueba mínima en producción (PM-1…PM-8) y ensayo de restauración en Azure sin ejecutar.
- Participación de Java: 28,78 % del producto (41,56 % con pruebas) frente al 60 % estimado en el APF2; métrica informativa (RA-02), explicada en el informe §11.1.
- Validación de las reglas con una barbería real (caso académico).

## Qué no contiene este paquete
Secretos, la carpeta `.local/`, bases de datos, dependencias instaladas (`node_modules/`, `target/`), cachés ni registros. Todos los datos de las capturas son ficticios (`@ejemplo.test`).
