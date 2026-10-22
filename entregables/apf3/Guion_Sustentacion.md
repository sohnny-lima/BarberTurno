# BarberTurno · APF3 · Guion de sustentación

> **Propuesta.** El aula no fija una duración ni una estructura para la exposición. Esta es una propuesta de **10–12 minutos** (unos 4 de demostración) que sigue el orden de la presentación `APF3_BarberTurno_Sustentacion.pptx` y del informe.
> Las frases entre comillas son una guía: conviene decirlas con palabras propias. Cada respuesta remite al archivo que se puede abrir si el docente pide ver el código.

---

## 1. Antes de exponer (lista de comprobación)

- [ ] La demostración local arranca (sección 4) y la base de demostración tiene los datos ficticios.
- [ ] El navegador tiene abiertas tres ventanas: cliente, barbero (`carlos@ejemplo.test`) y administrador (la cuenta ADMIN inicial de `BT_ADMIN_CORREO`, con su propia contraseña; `admin-demo@ejemplo.test` solo existe si la base no tenía otro ADMIN activo), cada una en un perfil o ventana privada distinta.
- [ ] VS Code abierto en el repositorio con estos archivos en pestañas: `ReservaService.java`, `ReservaRepository.java`, `SecurityConfig.java`, `V1__esquema_inicial.sql`, `ReporteExcelService.java` y `ConcurrenciaReservaIT.java`.
- [ ] GitHub abierto en `https://github.com/sohnny-lima/BarberTurno` (pestañas *Commits* y *Actions*).
- [ ] Producción abierta en `https://barberturno-web-202610-bvbacecef3hpcga6.westus-01.azurewebsites.net` (solo para mostrar que responde; la demostración se hace en local).
- [ ] Ensayo con cronómetro al menos dos veces.

---

## 2. Exposición por bloques

### Bloque 1 · Contexto y continuidad (1 min) — diapositivas 1–3
«Buenas noches, profesor. Presento el tercer avance de BarberTurno, un sistema web de reservas para una barbería de una sede en Huamanga. En el APF2 presenté el análisis, tres alternativas y un prototipo HTML que simulaba las pantallas. En este avance muestro el sistema construido: la alternativa A1, con Angular, una API en Java con Spring Boot y PostgreSQL. El alcance no cambió: tres roles, una sede y hasta diez barberos.»

**Idea clave:** el APF2 era diseño y prototipo; el APF3 es el producto funcionando, con evidencias por commit.

### Bloque 2 · Arquitectura y recorrido de una petición (2 min) — diapositivas 4–5
«La aplicación es un monolito modular: un solo jar sirve la interfaz Angular y la API. Cuando un cliente confirma una reserva, Angular envía un POST con la cookie de sesión y el token CSRF. Spring Security valida el JWT, el CSRF y el rol. El controlador recibe el DTO validado y llama al servicio. El servicio abre una transacción, bloquea al cliente y al barbero, vuelve a comprobar la disponibilidad y guarda la reserva, la auditoría y los avisos. Si algo falla, se revierte todo.»

**Si piden el código:** `ReservaService.crear` (líneas del bloqueo ① y ②) y el diagrama de secuencia del informe.

### Bloque 3 · Patrones y seguridad (2 min) — diapositivas 6–8
«Apliqué MVC en el backend: los controladores REST son el controlador, los servicios y entidades son el modelo y la vista es el JSON que Angular presenta. DAO con los repositorios de Spring Data: los servicios no escriben SQL. De SOLID muestro responsabilidad única en `PoliticaTransiciones`, inversión de dependencias con el `Clock` inyectado y segregación de interfaces con `UsuarioActual`. TDD lo apliqué de forma documentada en T-49 y en T-58: primero la prueba en rojo y luego la corrección; el resto del proyecto tiene pruebas automatizadas, pero no fue TDD estricto.»
«En seguridad: BCrypt, JWT en cookie HttpOnly, CSRF en toda escritura, autorización por rol en Spring Security y por propiedad en el servicio, con 404 ante reservas ajenas.»

### Bloque 4 · Bibliotecas Java (1,5 min) — diapositiva 9
«Logback ya registraba los eventos del sistema, en formato estructurado en producción y sin datos personales. Para usar de verdad POI, Commons y Guava añadí una función útil: exportar a Excel el reporte del administrador. POI genera el archivo; Commons Lang detecta los textos que Excel podría interpretar como fórmula y les aplica un estilo que los fuerza a texto sin cambiar el dato; Guava comprueba que los totales cuadren antes de entregar el archivo y mide el tiempo; y Logback registra la exportación sin datos personales.»

### Bloque 5 · Demostración (4 min) — diapositiva 10
Seguir la sección 4 de este guion.

### Bloque 6 · Git, pruebas y despliegue (1,5 min) — diapositivas 11–13
«Todo el proyecto está en GitHub: 450 commits, una rama por tarea integrada con merge sin avance rápido y commits convencionales que citan la tarea. Cada push ejecuta la CI: backend con PostgreSQL real y frontend con lint, pruebas y build. La versión publicada pasó 1548 pruebas de backend, 442 de frontend y 48 casos E2E. Está desplegada en Azure for Students con HTTPS y un monitor de salud cada cinco minutos.»

### Bloque 7 · Pendientes y cierre (0,5 min) — diapositivas 14–15
«Quedan pendientes, y los declaro como tales: la prueba de usabilidad con cinco personas, la medición de disponibilidad de noviembre y la prueba funcional mínima en producción. Muchas gracias.»

---

## 3. Declaración sobre el uso de IA (decirla si se pregunta, sin evasivas)
«Desarrollé el proyecto con asistentes de IA: Claude Code como arquitecto y revisor, Codex como implementador y Gemini como revisor independiente. Yo definí el alcance, tomé las decisiones, aprobé cada integración y desplegué en Azure. GitHub muestra la coautoría de Claude en los commits. Lo que puedo demostrar es que entiendo el código: puedo explicar cualquier parte del recorrido de una reserva y por qué está así.»

---

## 4. Demostración local (4 minutos)

**Preparación (antes de la clase):**
1. Fijar Java 21: `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'`.
2. Construir el jar con la interfaz incluida desde la versión que se presente: `cd backend` y `.\mvnw.cmd clean package -Pcon-frontend -DskipTests` (requiere Node 24.21.0 con fnm).
3. Definir la clave de demostración: `$env:BT_DEMO_PASSWORD = 'DemoSustentacion2026'` (valor público y ficticio).
4. Arrancar desde la raíz con la base de demostración y el reloj fijo del escenario del APF2:
   `java -jar backend/target/barberturno-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev,demo --spring.datasource.url=jdbc:postgresql://localhost:5433/barberturno_demo_t32 --barberturno.reloj-fijo=2026-09-28T09:00:00-05:00`
5. Abrir `http://localhost:8080`.

**Recorrido:**
| Paso | Rol | Acción | Qué decir |
|---|---|---|---|
| 1 | Cliente (`cliente@ejemplo.test`) | Reservar → Corte clásico → Carlos → 01/10/2026 → elegir una franja → revisar → confirmar | «Solo veo franjas libres; el servidor vuelve a comprobar al confirmar.» |
| 2 | Cliente | Mis citas: ver la nueva cita y su aviso; abrir Reprogramar | «Los botones vienen de los permisos que calcula el servidor; la política de 2 h está en Java.» |
| 3 | Barbero (`carlos@ejemplo.test`) | Agenda del 01/10: ver la cita; abrir acciones | «El barbero solo ve su agenda; si pide una reserva ajena, el servidor responde 404.» |
| 4 | Administrador | Horarios: intentar un bloqueo que cruza una cita | «El servidor lo rechaza porque hay una reserva vigente (CP-07).» |
| 5 | Administrador | Reportes: aplicar el periodo de octubre → **Descargar Excel** y abrir el archivo | «Las cifras coinciden con la pantalla; los textos se escriben como texto, nunca como fórmulas.» |
| 6 | (opcional) | Mostrar en VS Code `ConcurrenciaReservaIT` | «Diez solicitudes simultáneas a la misma franja: una se confirma y nueve reciben 409.» |

**Si algo falla en vivo:** mostrar las capturas del informe (sección 8) y la ejecución de CI en GitHub; explicar qué debía ocurrir.

---

## 5. Preguntas de defensa con respuesta

### Arquitectura
**¿Por qué un monolito y no microservicios?**
Un desarrollador, una sede y pocas reservas al día. Un solo proceso y una sola base permiten transacciones ACID que guardan reserva, auditoría y aviso juntas. Los módulos por funcionalidad (`reservations`, `scheduling`, `catalog`…) mantienen la separación sin el coste operativo de varios servicios.

**¿Dónde está la lógica de negocio?**
Solo en Java: servicios (`ReservaService`, `DisponibilidadService`) y reglas puras (`ReglasTemporales`, `PoliticaTransiciones`, `CalculadoraFranjas`). Angular solo valida formularios para la experiencia y habilita botones según `ReservaDto.permisos`.

**Recorrido de una petición.** Angular → filtros de Spring Security (JWT en cookie, CSRF, rol) → `ReservaController` (DTO validado) → `ReservaService` (transacción, bloqueos, revalidación) → repositorios JPA → PostgreSQL → respuesta 201 con `ReservaDto` o un `application/problem+json` con el código de error.

### MVC, DAO y SOLID
**¿Dónde está la vista si no hay Thymeleaf?**
En una API REST la vista del backend es la representación JSON que produce el controlador; la presentación visual la hace Angular en el navegador. Spring Web MVC sigue siendo el marco: `DispatcherServlet`, controladores y conversión de mensajes.

**¿Qué es un DAO aquí?**
Las interfaces `*Repository` que extienden `JpaRepository`. Encapsulan el acceso a datos con métodos de negocio (`buscarSolapamientos`, `bloquearPorIds`); Spring Data genera la implementación. Los servicios no escriben SQL.

**Dé un ejemplo de SOLID.**
SRP: `PoliticaTransiciones` solo decide si un cambio de estado es válido; no toca la base de datos. DIP: `ReservaService` recibe un `Clock` por constructor; en producción es el reloj real y en pruebas uno fijo, así se prueban reglas de fechas sin esperar. ISP: los servicios dependen de `UsuarioActual` (tres métodos) y no del contexto completo de Spring Security.

### TDD
**¿Aplicó TDD?**
En dos tareas documentadas: T-49 (primero la prueba que reproducía el 403 de Firefox, luego la corrección) y T-58 (prueba en rojo, implementación en verde, por ciclos). El resto del proyecto tiene muchas pruebas automatizadas, pero escritas junto con la implementación: eso no es TDD estricto y no lo presento como tal.

**¿Qué diferencia hay entre TDD y tener pruebas?**
TDD es un orden de trabajo: la prueba guía el diseño y se ve fallar antes de existir el código. Tener pruebas solo indica que el comportamiento está verificado, no cómo se llegó a él.

### Repositorios, transacciones y concurrencia
**¿Por qué no basta con consultar la disponibilidad y luego guardar?**
Dos clientes pueden consultar la misma franja libre antes de que alguno guarde; ambos la verían libre y se crearían dos citas. Por eso se bloquea la fila del barbero y **después** se vuelve a comprobar dentro de la misma transacción.

**¿Qué pasa si dos personas reservan a la vez?**
La primera toma el bloqueo de la fila del barbero; la segunda espera. Cuando la primera confirma, la segunda continúa, vuelve a comprobar, ve la cita nueva y recibe 409 `FRANJA_NO_DISPONIBLE`. `ConcurrenciaReservaIT` lo prueba con 10 solicitudes simultáneas: 1 × 201 y 9 × 409.

**¿Y si alguien olvida el bloqueo en código nuevo?**
La base tiene restricciones `EXCLUDE USING gist` que impiden dos reservas activas solapadas del mismo barbero o del mismo cliente. Es la red de seguridad.

**¿Cómo evita interbloqueos?**
Todas las operaciones toman los bloqueos en el mismo orden: cliente → barberos por id → reserva.

**¿Para qué sirve `version`?**
Bloqueo optimista: si dos pestañas actúan sobre la misma reserva, la segunda envía una versión vieja y recibe 409 `VERSION_DESACTUALIZADA`.

### Autenticación, autorización y CSRF
**¿Cómo se autentica?**
Correo y contraseña (BCrypt, coste 12). El servidor emite un JWT de 8 h en la cookie `BT_SESION`, HttpOnly y `SameSite=Strict` (y `Secure` en producción). Tras 5 intentos fallidos la cuenta se bloquea 15 minutos.

**¿Cómo se autoriza?**
Por rol en `SecurityConfig` (cada ruta y método) y por propiedad en el servicio (`ReservaAutorizacion`): una reserva ajena responde 404 para no revelar que existe.

**¿Qué fue la corrección CSRF?**
Spring Security desactiva CSRF cuando hay un JWT porque supone que va en una cabecera. Aquí va en una cookie, que el navegador envía solo; sin CSRF, otro sitio podría provocar escrituras. Se fuerza `DEFAULT_CSRF_MATCHER` y cada endpoint de escritura tiene pruebas «sin CSRF → 403» y «CSRF incorrecto → 403». Además, en T-49 se estabilizó el token para que Firefox no recibiera 403 intermitentes y se entrega un token nuevo al iniciar o cerrar sesión.

### Bibliotecas Java
**Logback y SLF4J, ¿no son lo mismo?**
SLF4J es la fachada que usa el código (`LoggerFactory.getLogger`); Logback es la implementación que formatea y escribe. En producción el formato es ECS (JSON). Las pruebas usan `ListAppender` de Logback para comprobar que no se registran correos ni secretos.

**¿Por qué Apache POI?**
Para generar el `.xlsx` del reporte: crea el libro, las hojas, las celdas y los estilos. Solo se escriben textos y números; nunca fórmulas.

**¿Para qué Commons Lang si Java ya tiene `String`?**
`StringUtils.startsWithAny` expresa en una línea la comprobación de los prefijos peligrosos (`=`, `+`, `-`, `@`, tabulación, retorno de carro) de la inyección de fórmulas descrita por OWASP. A esas celdas se les aplica el estilo `quotePrefixed`, que obliga a Excel a tratarlas como texto **sin cambiar el valor**.

**¿Para qué Guava?**
`Preconditions.checkState` verifica que la suma por estado, por servicio y por profesional coincida con el total antes de escribir: si no cuadra, no se entrega un archivo incoherente. `Stopwatch` mide la generación para registrarla en Logback.

**¿No es artificial añadirlas?**
Se añadieron para una función útil (el administrador conserva o comparte el resumen) y acotada, sin tocar las reglas de reserva. Se descartaron usos que alterarían datos o reescribirían reglas críticas en pleno freeze.

### Git, CI, pruebas y despliegue
**¿Cómo organizó el trabajo en Git?**
Una rama `tarea/T-XX` por tarea, commits convencionales en español con `[T-XX]` e integración con `merge --no-ff`. `docs/tareas.md` guarda los hashes y las notas de cierre de cada tarea.

**¿Qué hace la CI?**
En cada push: backend `mvnw verify` con un PostgreSQL 18 real (pruebas unitarias, de integración, JaCoCo y Javadoc sin avisos) y frontend lint, formato, Vitest y build de producción.

**¿Cómo está desplegado?**
Azure App Service Linux B1 con Java 21 y PostgreSQL Flexible Server 18.6; un único jar; HTTPS obligatorio; *health check* y UptimeRobot cada 5 minutos. La versión desplegada es la de `d2f3107`; la exportación a Excel (T-58) está en `main` (`3bcd5d0`) pero no se ha desplegado por decisión mía, así que la muestro en local.

### Participación de Java
**En el APF2 previó 60 % de Java. ¿Cuánto tiene?**
Con el mismo método que comprometí (líneas propias sin comentarios, vacías, dependencias ni generados), Java es el 28,78 % del código de producto y el 41,56 % con pruebas. No se cumple la estimación. La caída viene de la interfaz: en las fases visuales crecieron sobre todo los estilos y las plantillas (unas 4600 líneas), mientras Java siguió creciendo. Toda la lógica de negocio, la seguridad y la persistencia están en Java. El 04/10 reclasifiqué el porcentaje como métrica informativa; no cambié el método ni añadí código artificial para subirlo. GitHub muestra 50,1 % porque mide bytes con otro criterio, y no lo uso como evidencia.

### Limitaciones y evolución
**¿Qué falta?**
CP-11 (usabilidad con cinco personas), RNF-02 (disponibilidad mensual de noviembre), la prueba funcional mínima en producción, el ensayo de restauración en Azure y la validación de las reglas con una barbería real.

**¿Cómo evolucionaría?**
Como propuestas futuras, no implementadas: varias barberías en una misma plataforma, roles de propietario, reservas por WhatsApp y asistencia con IA. Requerirían nuevos requisitos y otra fase del proyecto.

---

## 6. Archivos que conviene saber abrir
| Tema | Archivo |
|---|---|
| Reserva y concurrencia | `backend/src/main/java/pe/barberturno/reservations/ReservaService.java` (`crear`) |
| DAO | `reservations/ReservaRepository.java`, `scheduling/BarberoRepository.java` |
| Reglas puras | `reservations/ReglasTemporales.java`, `reservations/PoliticaTransiciones.java` |
| Seguridad | `common/security/SecurityConfig.java`, `auth/AuthService.java` (`login`) |
| Base de datos | `backend/src/main/resources/db/migration/V1__esquema_inicial.sql` |
| Exportación (T-58) | `reporting/ReporteExcelService.java` y sus pruebas (T-58, en `main` desde `3bcd5d0`) |
| Prueba de concurrencia | `backend/src/test/java/pe/barberturno/reservations/ConcurrenciaReservaIT.java` |
| CI | `.github/workflows/ci.yml` |
