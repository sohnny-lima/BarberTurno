# V2-TW · Evidencia de producción · Codex 155

Fecha: 11/11/2026. Rama existente `tarea/V2-TWILIO-sandbox`, base del encargo `c0d0078`
(contrato sobre `2f76afb`). Alcance: código de producción y una única prueba nueva, `FirmaTwilioTest`.
Las pruebas nuevas CT-1…CT-8, el verify completo y el recorrido real quedan a Gemini/Claude según el reparto.

## Implementación

- `whatsapp/twilio`: propiedades enlazadas solo en el perfil, firma HMAC-SHA1 del JDK, lectura cruda
  del formulario con límite de 1 MiB y dos callbacks exactos; entrada y estados traducidos al JSON
  que recibe `RecepcionWebhookService`. Sin acceso al negocio antes de autenticar.
- `ClienteTwilioWhatsApp`: implementación primaria solo con `twilio`, autenticación básica, formulario
  con From/To/Body/StatusCallback, sin redirecciones ni reintentos propios. Solo 201 con sid utilizable
  acredita aceptación. 429/5xx transitorios, otros rechazos permanentes con código de Twilio; la
  incertidumbre de red es `Desconocido`. No se registra ninguna excepción HTTP ni el contenido.
- Menús de botones/listas con numeración global en el orden de secciones, incluyendo descripción
  (conserva la fecha completa de las franjas). Texto posterior conserva el menú. Plantilla o Body
  superior a 1600 caracteres: rechazo permanente local, sin truncar ni enviar.
- Correspondencia por wa_id con caducidad lógica de 24 h y consumo atómico al elegir. Las respuestas
  numéricas repetidas conservan su traducción por MessageSid durante 24 h para evitar consumir un
  menú posterior. No sustituye la idempotencia de la cola en PostgreSQL; la limpieza en memoria es perezosa.
- `ClienteGraphWhatsApp.configurado()`, implementado por ambos transportes: el enviador no reclama
  mensajes si falta configuración del cliente activo. Graph mantiene la comprobación previa del token.
  El método por defecto preserva la interfaz funcional de los dobles existentes, sin editar sus pruebas.
- `SecurityConfig` y `ResolutorNegocio`: solo los dos POST globales exactos de Twilio, con perfil activo;
  sin él siguen en 404. Se conserva DEFAULT_CSRF_MATCHER y la exención exacta de Meta. La guarda de
  producción rechaza `prod,twilio` antes de crear conexiones y conserva los mensajes previos para otros perfiles.
- Registro al arrancar en orden 200, después de las demos 100/101. El servicio bloquea primero negocio
  y luego canal, usa `CanalWhatsAppRepository`, no cambia un canal diferente y no escribe si el mismo
  ya está activo. Se ampliaron con Javadoc los miembros de `CanalWhatsApp` necesarios desde el subpaquete.
  Los dígitos del Sandbox sirven de waba_id sintético; no se guarda AccountSid en la tabla. No se
  atribuye una auditoría de plataforma a un actor ficticio. El procesador y el enviador existentes
  mantienen la comprobación de negocio operativo y propietario, sin crear ni conceder membresías.
- `application-twilio.yml`, nombres nuevos vacíos en la plantilla de entorno y guía de uso en README.
  Se evita el aviso engañoso de outbox pendiente por falta de token de Graph cuando el transporte es Twilio.

## Firma: fuente del vector

[RequestValidatorTest del SDK oficial twilio-java](https://github.com/twilio/twilio-java/blob/main/src/test/java/com/twilio/security/RequestValidatorTest.java),
`setUp` y `testValidate`, consultados el 11/11/2026. URL pública de ejemplo, token ficticio `12345`,
cinco parámetros del vector y firma esperada `RSOYDt4T1cUTdK1PDd93/VVr8B8=`. La prueba usa el
resultado literal oficial, sin generar la expectativa con el algoritmo probado. El Javadoc de
`FirmaTwilio` enlaza también el código oficial de `RequestValidator`.

El contrato exige la URL exacta: no se reproducen las tolerancias del SDK para añadir/quitar puertos.
Se comparan los bytes del HMAC mediante `MessageDigest.isEqual`. Los parámetros duplicados, consultas
no configuradas y formularios ambiguos se rechazan con 401 antes de traducir.

## Intervalo, caducidad y límites

El cliente singleton serializa el HTTP con `synchronized`. Mide la espera con `System.nanoTime`,
independientemente del Clock usado para fechas, y repite la espera si despierta antes de tiempo.
Se esperan al menos 3 s desde que termina una petición hasta que empieza la siguiente; por ello,
también se cumple la separación entre inicios. Todos los mensajes de un lote y los de lotes/hilos
posteriores comparten la misma espera. No se retiene una transacción ni conexión de PostgreSQL
durante esa espera ni durante el HTTP. Interrumpir la espera conserva la interrupción y devuelve
`NoEnviado` sin emitir la petición. El intervalo configurado se valida como mínimo de 3 s.

Si la correspondencia caduca, se consume o se pierde al reiniciar, una nueva respuesta numérica llega
como texto a la conversación; la persona recupera el menú escribiendo «menú». El enviador no necesita
una correspondencia anterior para enviar: un nuevo interactivo aceptado instala otra durante 24 h.
Ante un resultado desconocido también la conserva, porque el mensaje pudo entregarse; un rechazo
probado conserva el menú previo. Los textos no la eliminan.

Limitaciones del contrato: una sola instancia; correspondencias e intervalo no se comparten entre
procesos ni sobreviven al reinicio. Sin biz_opaque_callback_data no se concilia un envío incierto por
identificador interno: persisten los posibles duplicados de los reintentos y el límite de la outbox.
No hay evidencia de Twilio real, inscripción del teléfono, ventana real ni recorrido CT-6 en este encargo.

## Entorno y órdenes

Windows / PowerShell 7.6.10; Git 2.45.1.windows.1; Temurin 21.0.8+9; Spring Boot 4.1.1;
PostgreSQL 18.6 en 5433 (versión informada por el contexto de prueba). No se usó Node ni frontend.

Todas las ejecuciones Maven se hacen en serie con:

```powershell
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-twilio -Registro target\<registro>.log <argumentos>
```

El ejecutor carga valores solo al entorno del proceso y fuerza `barberturno_v2_test`. Ningún valor
de `.local/barberturno.env` se muestra ni se copia a esta evidencia. No se ejecuta la aplicación en paralelo.

| Argumentos | Registro local, ignorado por Git | Resultado |
|---|---|---|
| `-Dtest=FirmaTwilioTest test` | `backend/target/v2-tw-firma.log` | 1 prueba; 0 fallos, errores y omisiones; BUILD SUCCESS, 1:31 min |
| `-Dtest=ClienteGraphWhatsAppTest,EnviadorMensajesWhatsAppIT,SalidaWhatsAppIT,PresupuestoEnvioWhatsAppIT,EstadosMensajesWhatsAppIT,WebhookWhatsAppControllerTest,ExencionCsrfWebhookTest,AuthCsrfHttpIT test` | `backend/target/v2-tw-regresion.log` | 154 pruebas; 0 fallos, errores y omisiones; BUILD SUCCESS, 3:58 min |
| `-DskipTests verify` | `backend/target/v2-tw-verify.log` | Compilación/empaquetado correctos; BUILD FAILURE en JaCoCo por cobertura de la selección parcial, 12,728 s; no alcanzó Javadoc |
| `-DskipTests javadoc:javadoc` | `backend/target/v2-tw-javadoc.log` | Falló por tres descripciones principales ausentes en los accesores nuevos de CanalWhatsApp; corregidas, sin cambiar código ejecutable |
| `-DskipTests javadoc:javadoc` (repetición) | `backend/target/v2-tw-javadoc-2.log` | BUILD SUCCESS en 24,056 s; 0 avisos y errores de Javadoc |

Desglose de la regresión: AuthCsrfHttpIT 4; ExencionCsrfWebhookTest 12; ClienteGraphWhatsAppTest 45;
EnviadorMensajesWhatsAppIT 33; EstadosMensajesWhatsAppIT 18; PresupuestoEnvioWhatsAppIT 14;
SalidaWhatsAppIT 24; WebhookWhatsAppControllerTest 4. Ninguna prueba existente se modificó.

## Incidencias de ejecución

El primer intento no llegó a Maven: el sandbox denegó el cerrojo externo. Se reintentó con escalación
automática conforme al encargo, sin cambiar ACL, propietario ni configuración:

```text
mvn-v2-test.ps1: Excepción al llamar a "Open" con "4" argumentos: "Access to the path 'D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock' is denied."
```

En el primer reintento faltaba `backend/target`; el ejecutor no llegó a lanzar Maven y devolvió código
0 pese al error de redirección. No se contó como verificación aprobada. Se creó solo ese directorio
local ignorado por Git y se repitió la prueba, ya con informe Surefire y BUILD SUCCESS:

```text
Could not find a part of the path 'D:\Proyectos\BarberTurno-v2-twilio\backend\target\v2-tw-firma.log'.
```

El índice Git del worktree también necesitó escalación; el reintento de `git add` terminó correctamente
sin alterar ACL, propietario, historial ni configuración:

```text
fatal: Unable to create 'D:/Proyectos/BarberTurno/.git/worktrees/BarberTurno-v2-twilio/index.lock': Permission denied
```

**Hallazgo de verificación para el coordinador:** `-DskipTests verify` no desactiva la comprobación de
cobertura. En un target nuevo que solo ejecutó las suites solicitadas, JaCoCo exige también el 70 % de
reservas, agenda y fichas; se detiene antes de Javadoc. No se modifica el pom, el umbral ni jacoco.exec,
ni se ejecuta la suite completa reservada a Claude. El cierre queda bloqueado hasta esa ejecución.
Salida del fallo:

```text
[WARNING] Rule violated for package pe.barberturno.scheduling: lines covered ratio is 0.14, but expected minimum is 0.70
[WARNING] Rule violated for package pe.barberturno.reservations.dto: lines covered ratio is 0.00, but expected minimum is 0.70
[WARNING] Rule violated for package pe.barberturno.reservations: lines covered ratio is 0.07, but expected minimum is 0.70
[WARNING] Rule violated for package pe.barberturno.customers: lines covered ratio is 0.24, but expected minimum is 0.70
[WARNING] Rule violated for package pe.barberturno.customers.dto: lines covered ratio is 0.00, but expected minimum is 0.70
[WARNING] Rule violated for package pe.barberturno.scheduling.dto: lines covered ratio is 0.00, but expected minimum is 0.70
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.15:check (check-dominio) on project barberturno: Coverage checks have not been met.
```

El Javadoc separado identificó tres errores propios de documentación y se corrigieron antes de
cerrar el commit: los comentarios tenían solo `@return`, sin descripción principal. No cambió código
ejecutable después de las pruebas. Salida del primer intento (detalles completos en el registro):

```text
[WARNING] CanalWhatsApp.java:76: warning: no main description
[WARNING] CanalWhatsApp.java:79: warning: no main description
[WARNING] CanalWhatsApp.java:83: warning: no main description
[WARNING] 3 warnings
[INFO] BUILD FAILURE
[ERROR] An error has occurred in Javadoc report generation: Project contains Javadoc Warnings
```

## Javadoc y cierre

Javadoc nuevo para las nueve clases de `whatsapp.twilio`, sus constructores/métodos públicos y
la selección inmutable del menú. También para `configurado()` y los miembros de canal expuestos;
se amplió el de la seguridad, el resolutor, la configuración y el enviador con sus reglas respectivas.

**Estado:** código implementado; cierre **Bloqueada (JaCoCo requiere la suite completa reservada a
Claude)**. Seguimiento en `docs/v2/10-backlog.md`, aplicando la sustitución de `docs/tareas.md` que
establece AGENTS §5.1 para v2. No se marca Hecha ni se acredita la definición completa sin ese check
y las pruebas nuevas de Gemini. No se detectó necesidad de cambiar contratos, DDL ni reglas de negocio.

Commits:

- `2f1ababfd299a777edebb1ea3a7478890136c8b7` · transporte activo y avisos.
- `4f0289a6aebfbcd5a6b0c52f3a2b24a88ffcd50a` · adaptador, seguridad, configuración y vector.
- Cierre documental por asunto: `docs(whatsapp): registrar verificación parcial y bloqueo de cobertura [V2-TW]`;
  el hash de ese mismo commit se entrega en el informe final para evitar una autorreferencia imposible.
- Merge: ninguno, por instrucción expresa. No hay push ni integración en v2/main.

Comprobaciones finales: `git diff --check` y `git diff --cached --check` sin errores antes de cada commit;
UTF-8 sin BOM y LF en archivos modificados; §1–§7 del contrato idénticos a `c0d0078`. Sin cambios en
documentos protegidos, frontend, migraciones, APF2, entregables ni evidencias de v1. No se leyó
`.local/coordinacion/`, no se mostraron valores de `.local/` ni se usaron credenciales de Twilio,
ngrok, Azure, números reales o servicios de pago. Solo bases de prueba de v2 mediante el ejecutor.
Sin ramas, etiquetas o worktrees nuevos; sin cambios de remoto, historial, permisos o configuración.

Tras finalizar Maven: **0 procesos Java**, **0 escuchas en 8080/8081/4200**, **cerrojo ausente**. Todas
las sesiones del ejecutor terminaron; no se arrancó manualmente ninguna aplicación ni servidor.
