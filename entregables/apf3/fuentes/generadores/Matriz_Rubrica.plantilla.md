# BarberTurno · APF3 · Matriz de la rúbrica SI53-APF3-2026A

Autoevaluación preliminar con la evidencia disponible al {{FECHA}}. **No sustituye la evaluación del docente** ni promete un puntaje. Las páginas se refieren a `APF3_BarberTurno_Informe.pdf`.

Commit de referencia del código: **{{COMMIT_REF}}**. Versión desplegada en Azure: **`d2f3107`**. Repositorio: https://github.com/sohnny-lima/BarberTurno

| # | Criterio | Máx. | Evidencia exigida | Evidencia encontrada | Archivo / commit / prueba | Sección y página del informe | Brecha pendiente |
|---|---|---:|---|---|---|---|---|
| 1 | Diseño de la solución | 3 | Arquitectura inicial; al menos tres de MVC, TDD, DAO y SOLID; seguridad | **MVC** (Spring Web MVC: controladores REST, servicios/entidades, vista JSON + Angular); **DAO** (9 repositorios Spring Data JPA, bloqueos y consultas de solape); **SOLID** (SRP `PoliticaTransiciones`, DIP `Clock` y constructores, ISP `UsuarioActual`); **TDD** parcial y declarado (T-49 y T-58); seguridad completa (BCrypt, JWT en cookie, CSRF, rol + propiedad, cabeceras, HTTPS) | `ReporteController.java`, `ReservaRepository.java`, `BarberoRepository.java`, `PoliticaTransiciones.java`, `ClockConfig.java`, `UsuarioActual.java`, `SecurityConfig.java`; commits `e9ddeed`→`c669276`, `21f86ad`→`b60cbdc`{{TDD_T58}} | §3 (p. {{P3}}), §4 (p. {{P4}}), §5 (p. {{P5}}), §6 (p. {{P6}}) | Ninguna esencial. TDD no es la práctica general del proyecto y así se declara |
| 2 | Uso de recursos Java | 2 | Guava, POI, Commons y Logback incorporados con uso real y seguridad básica | {{C2_EVIDENCIA}} | {{C2_ARCHIVOS}} | §7 (p. {{P7}}) | {{C2_BRECHA}} |
| 3 | Control de versiones | 3 | Git en el proyecto y evidencia en GitHub del 100 % de avances y actualizaciones de los entregables | Repositorio público; {{COMMITS}} commits en `main` desde el 01/10/2026; {{MERGES}} merges `--no-ff` de tareas; commits convencionales con `[T-XX]`; CI en cada push (39099141093 success en `d2f3107`); capturas auténticas del repositorio, del historial y de Actions | GitHub; `docs/tareas.md`; `.github/workflows/ci.yml`; `evidencias/github/` | §10 (p. {{P10}}) | {{C3_BRECHA}} |
| 4 | Interfaces gráficas | 6 | Interfaces implementadas y funcionando que cubran el 100 % del alcance comprometido | P01–P08 del APF2 implementadas para los tres roles (más usuarios y reserva asistida); E2E 48/48 en Chromium y Firefox a 360 y 1440 px; capturas con axe 0 | `frontend/src/app/features/**`; `frontend/e2e/recorrido.spec.ts`; `evidencias/interfaces/` | §8 (p. {{P8}}), §9 (p. {{P9}}) | {{C4_BRECHA}} |
| 5 | Construcción del producto | 4 | Completa, coherente (documentación = código), buenas prácticas, autoría o dominio | **Completa:** RF-01…RF-14 + RF-15…RF-21. **Coherente:** requisitos → arquitectura → tareas → código con IDs estables; matriz requisito → pantalla → implementación → prueba. **Buenas prácticas:** capas, DAO, transacciones, Javadoc con doclint, CI, JaCoCo, commits convencionales. **Autoría/dominio:** uso de IA declarado; guion con recorrido y preguntas de defensa | `docs/requisitos.md`, `docs/arquitectura.md`, `docs/tareas.md`, `docs/pruebas/aceptacion.md`; `Guion_Sustentacion.md` | §9, §11–§14 (p. {{P11}}–{{P14}}) | **Autoría/dominio depende de la sustentación:** el código se desarrolló con asistentes de IA; el responsable debe demostrar que lo explica. Pendientes no funcionales (CP-11, RNF-02, PM, restauración en Azure) declarados |
| 6 | Sustentación oral | 2 | Secuencia lógica, fluidez, coherencia, dominio vinculado a los conceptos del curso | Presentación de 15 diapositivas con notas; guion por bloques (propuesta de 10–12 min); demostración de 4 min; preguntas de defensa con respuestas basadas en el código | `APF3_BarberTurno_Sustentacion.pptx`, `Guion_Sustentacion.md` | §15 (p. {{P15}}) | Depende del ensayo y de la exposición |

## Brechas que requieren decisión o evidencia del responsable
{{BRECHAS}}

## Notas de método
- Una dependencia transitiva no se cuenta como uso de una biblioteca (criterio 2).
- Muchas pruebas no se presentan como TDD (criterio 1).
- Las cifras de pruebas indican su commit y fecha; las del checkpoint no se presentan como ejecuciones nuevas.
- La rúbrica pide «el 100 % de los entregables» en GitHub (criterio 3): se distingue lo publicado de lo que sigue solo en local.
