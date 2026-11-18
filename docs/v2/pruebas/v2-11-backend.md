# V2-11 · X11 · Backend · Encargo 126

Fecha: 04/11/2026. Base: `c4218c8`. Rama exclusiva: `tarea/V2-11-derivacion-bandeja-backend`.
Estado: **En verificación de cierre (encargo 126b)**. H-126-01 autorizado y ajustado: siete campos y
`conversacionId` nulo en avisos de reserva, conservando las comprobaciones anteriores. Sin merge ni push.

Entorno comprobado: Temurin 21.0.8+9, Maven 3.9.16 (wrapper 3.3.4), Spring Boot 4.1.1,
Hibernate 7.4.5.Final y PostgreSQL 18.6 en localhost:5433. No se usa Node en este bloque.
Java se comprobó con `& 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\java.exe' -version`;
la distribución de Maven consta en `backend/.mvn/wrapper/maven-wrapper.properties`.

## Alcance implementado

- V116: dos ramas de conversación, motivos, autor personal, origen entrante, destino exclusivo del aviso,
  FK compuesta, índice de bandeja, permisos de columna, disparador de retirada y sustitución de las restrictivas.
- Derivación atómica con anulación de la operación vigente, vaciado del estado, acuse de sistema y avisos a
  gestores activos. Devolución idempotente y retorno automático por negocio con conversación bloqueada,
  revalidación, lotes y ninguna transacción exterior.
- Bandeja: aislamiento, membresía vigente revalidada tras los bloqueos, máscara genérica, orden estable,
  cursor positivo, historial de 50 mensajes, contenido visible, autor actual, estado y motivo de fallo.
- Respuestas `PERSONA` con texto y autor en un INSERT; las entradas generales rechazan ese origen.
  Ventana efectiva compartida con el enviador; las salidas `ASISTENTE` pendientes en modo humano se
  descartan como `MODO_HUMANO`, conservando los presupuestos y sin avisos. Los fallos `PERSONA` tampoco avisan.
- Retención: fases de contenido y operaciones con transacción por negocio y fase, lotes y `SKIP LOCKED`;
  operaciones caducadas en la igualdad del vencimiento, como `OperacionesConversacionService`.
- Anonimización exclusiva de PROPIETARIO: conversación → operaciones → ficha; revalida después de esperar,
  rechaza cuenta web, cualquier reserva activa (también pasada o `EN_ATENCION`) y cualquier mensaje pendiente.
  Marca primero y borra avisos, operaciones, mensajes y conversación; conserva reserva, auditoría y evento crudo.
  Ficha anonimizada no editable; el número queda disponible para una conversación nueva sin ficha.

## Órdenes de verificación

Desde la raíz del worktree, cada orden Maven se ejecuta con JDK 21 y el ejecutor serializado:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-compilar.log -DskipTests compile
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-esquema.log '-Dtest=DerivacionV116IT,ConversacionV115IT,ActualizacionV1IT,InstalacionNuevaIT,EsquemaV2IT,EsquemaIT,RepositoriosIT,RetencionWhatsAppIT' test
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-bandeja-atencion.log '-Dtest=BandejaWhatsAppIT,AtencionHumanaWhatsAppIT,ConversacionV115IT' test
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-bandeja-atencion-2.log '-Dtest=BandejaWhatsAppIT,AtencionHumanaWhatsAppIT' test
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-regresion.log '-Dtest=pe.barberturno.whatsapp.*,pe.barberturno.reservations.*,BandejaWhatsAppIT,DerivacionV116IT,ConversacionV115IT,ActualizacionV1IT,InstalacionNuevaIT,EsquemaV2IT,EsquemaIT,RepositoriosIT,RetencionWhatsAppIT,FichaClienteIT,ReservaFichaIT,ConsentimientoConversacionIT' test
$PruebasV211 = (Get-ChildItem backend/src/test/java/pe/barberturno/whatsapp,backend/src/test/java/pe/barberturno/reservations -File | Where-Object { $_.Name -match '(Test|IT)\.java$' } | Sort-Object BaseName | ForEach-Object BaseName) -join ','
# Primera invocación incorrecta: faltaba la fase test; no ejecutó pruebas ni modificó la BD.
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-whatsapp-reservas.log "-Dtest=$PruebasV211"
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-whatsapp-reservas-2.log "-Dtest=$PruebasV211" test
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-11-backend -Registro target\v2-11-whatsapp-reservas-3.log "-Dtest=$PruebasV211" test
```

| Registro (en `backend/target/`, no versionado) | Resultado |
|---|---|
| `v2-11-compilar.log` | BUILD SUCCESS; compilación inicial |
| `v2-11-esquema.log` | 77 pruebas, 1 fallo de expectativa, 0 errores, 0 omitidas; corregido |
| `v2-11-bandeja-atencion.log` | 34 pruebas, 1 fallo temporal de fixture y 1 error de permisos de fixture; corregidos |
| `v2-11-bandeja-atencion-2.log` | BUILD SUCCESS; 30 pruebas, 0 fallos, 0 errores, 0 omitidas |
| `v2-11-regresion.log` | BUILD SUCCESS; 122 pruebas, 0 fallos, 0 errores, 0 omitidas; el selector por paquete no incluyó WhatsApp/reservas |
| `v2-11-whatsapp-reservas.log` | BUILD FAILURE; faltaba fase Maven (1,931 s); cero pruebas ejecutadas |
| `v2-11-whatsapp-reservas-2.log` | Interrumpida por error del servicio; no se cuenta como ejecución completa. El coordinador confirmó proceso terminado y retiró el cerrojo huérfano |
| `v2-11-whatsapp-reservas-3.log` | En ejecución, 41 clases explícitas, incluida la corrección H-126-01 |

Salidas de los fallos corregidos:

```text
ConversacionV115IT.checks_nonceResultadoEstadoTipo_yUnicaVigente
expected: 1
 but was: 3
Tests run: 77, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE

No goals have been specified for this build.
BUILD FAILURE

AtencionHumanaWhatsAppIT.cierreEntreEncoladoYEnvio_fallaPersona_sinAvisosAdicionales
error_codigo=MENSAJE_CADUCADO (expected: VENTANA_CERRADA)
AtencionHumanaWhatsAppIT.anonimizar_borraAvisosOperacionesYMensajesConReserva_peroConservaReservaAuditoriaYCuerpoCrudo
BadSqlGrammar: update mensaje_whatsapp set reserva_id=? where id=?
Tests run: 34, Failures: 1, Errors: 1, Skipped: 0
BUILD FAILURE
```

La política elimina las tres operaciones terminadas del fixture y conserva la vigente; se comprueban ambos
resultados. La prueba de ventana ahora encola un minuto antes del límite efectivo y avanza un minuto,
sin alcanzar la caducidad de seis horas del mensaje. La reserva del mensaje se fija al INSERT, respetando
su inmutabilidad; no se amplían los permisos para preparar pruebas.

Selección explícita de WhatsApp/reservas (41 clases):

```text
AtencionHumanaWhatsAppIT,AuditoriaAvisosIT,AvisosWhatsAppPersonalIT,CanalWhatsAppRepositoryTest,
CancelacionIT,ClienteGraphWhatsAppTest,ColaEventosWhatsAppTest,ConcurrenciaReservaIT,
ConversacionOperacionesIT,CorridaManualIT,EnviadorMensajesWhatsAppIT,ErroresMvcIT,EstadoReservaTest,
EstadosMensajesWhatsAppIT,EventoEntranteRepositoryTest,ExtractorEventosWhatsAppTest,FirmaWebhookTest,
PlantillaUtilityTest,PoliticaReintentosTest,PoliticaTransicionesTest,PresupuestoEnvioWhatsAppIT,
ProcesadorEventosWhatsAppIT,ProgramacionWhatsAppTest,ReceptoresWhatsAppPorDefectoTest,
RegistroEntradaWhatsAppIT,ReglasTemporalesTest,ReporteExcelIT,ReporteIT,ReprogramacionIT,
ReservaAsistidaIT,ReservaConfirmacionManualIT,ReservaConsultaIT,ReservaCrearIT,ReservaDtoTest,
ReservaTest,SalidaWhatsAppIT,SalidaWhatsAppPropiedadesTest,TransicionesIT,UsuarioAdminIT,
WebhookWhatsAppControllerTest,WhatsAppPropiedadesTest
```

## Cobertura de aceptación y concurrencia

- CB-2, CB-6…12 y CB-16: `DerivacionV116IT`, `BandejaWhatsAppIT`, `AtencionHumanaWhatsAppIT`, pruebas
  afectadas de esquema, instalación, actualización, repositorios, retención, avisos, reservas y clientes.
- Cada POST nuevo: sesión sin CSRF y con CSRF incorrecto → 403; profesional, cliente y plataforma sin
  membresía → 403; anónimo con CSRF válido → 401; id ajeno bajo ámbito autorizado → 404.
- Ventana efectiva: igualdad, entrada nula y cierre entre encolado y envío. El vencimiento publicado es nominal.
- Carreras con dos hilos y transacciones reales: responder/devolver/anonimizar esperan a la entrega de la
  misma conversación; membresía revocada durante la espera; anonimización frente al bloqueo del mensaje
  del enviador; retorno automático omite conversaciones bloqueadas y revalida la actividad del personal.
- La suite usa bases existentes y `@DirtiesContext(AFTER_CLASS)` heredado; nunca se lanzan dos órdenes Maven
  con BD al mismo tiempo. El servidor Graph es falso y pertenece a las pruebas.

## Decisiones menores y límites para la integración

- Si aparece una conversación enlazada mientras la anonimización espera por la ficha, se rechaza con
  `409 CONFLICTO` para reintentar; no se bloquea esa conversación después de la ficha.
- La desactivación de retención se prueba construyendo propiedades explícitas con plazos nulos; el perfil
  `test` mantiene su configuración de 30/90/30 días, sin editar YAML.
- `TextosConversacion` del contrato aún lanza `UnsupportedOperationException` en los textos nuevos; las
  pruebas X11 emplean `@MockitoBean` solo para esos textos. El orquestador, `guardarEntrante`, los textos,
  el tercer botón y el frontend corresponden a C11/G11 y no se modifican aquí.
- La consulta de operatividad de la bandeja usa SQL actual del negocio, canal y propietario; la comprobación
  local de membresía también evita entidades potencialmente retenidas antes de esperar un bloqueo.
- Ante canal no operativo, el POST de respuesta usa el código existente `422 RECURSO_INACTIVO`; el DTO de
  permisos publica `NEGOCIO_NO_OPERATIVO`. La suspensión mantiene el `409 NEGOCIO_SUSPENDIDO` del filtro común.
- No se introduce otra versión, dependencia, contrato ni regla de negocio. P2-05 y P2-06 siguen pendientes.
  No se acreditan Meta real, privacidad aprobada ni E2E conjunto; el coordinador ejecutará el verify completo.
- Javadoc útil en todos los tipos y métodos públicos nuevos, transiciones y auxiliares de ventana/autorización;
  actualización de los contratos de avisos, DTO, guarda de ficha y descripción de las fases de retención.

## Inventario, commits y cierre

Inventario exacto de los 34 archivos con cambio final, relativo a la raíz:

```text
backend/src/main/java/pe/barberturno/audit/AccionAuditoria.java
backend/src/main/java/pe/barberturno/common/security/AutorizacionGestionLocal.java
backend/src/main/java/pe/barberturno/common/security/SecurityConfig.java
backend/src/main/java/pe/barberturno/conversation/DerivacionConversacionService.java
backend/src/main/java/pe/barberturno/conversation/ProgramadorRetornoWhatsApp.java
backend/src/main/java/pe/barberturno/conversation/RetencionWhatsApp.java
backend/src/main/java/pe/barberturno/customers/AnonimizacionFichas.java
backend/src/main/java/pe/barberturno/customers/AnonimizacionFichasController.java
backend/src/main/java/pe/barberturno/customers/Cliente.java
backend/src/main/java/pe/barberturno/notifications/Notificacion.java
backend/src/main/java/pe/barberturno/notifications/NotificacionService.java
backend/src/main/java/pe/barberturno/notifications/dto/NotificacionDto.java
backend/src/main/java/pe/barberturno/whatsapp/AvisoFalloWhatsApp.java
backend/src/main/java/pe/barberturno/whatsapp/BandejaWhatsApp.java
backend/src/main/java/pe/barberturno/whatsapp/BandejaWhatsAppController.java
backend/src/main/java/pe/barberturno/whatsapp/ColaSalidaWhatsApp.java
backend/src/main/java/pe/barberturno/whatsapp/EnviadorMensajesWhatsApp.java
backend/src/main/java/pe/barberturno/whatsapp/MotivoFallo.java
backend/src/main/java/pe/barberturno/whatsapp/SalidaWhatsAppService.java
backend/src/main/java/pe/barberturno/whatsapp/VentanaWhatsApp.java
backend/src/main/resources/db/migration/V116__derivacion_bandeja_retencion.sql
backend/src/test/java/pe/barberturno/RepositoriosIT.java
backend/src/test/java/pe/barberturno/common/tenancy/ActualizacionV1IT.java
backend/src/test/java/pe/barberturno/common/tenancy/BandejaWhatsAppIT.java
backend/src/test/java/pe/barberturno/common/tenancy/ConversacionV115IT.java
backend/src/test/java/pe/barberturno/common/tenancy/DerivacionV116IT.java
backend/src/test/java/pe/barberturno/common/tenancy/EsquemaV2IT.java
backend/src/test/java/pe/barberturno/common/tenancy/InstalacionNuevaIT.java
backend/src/test/java/pe/barberturno/common/tenancy/WhatsAppEsquemaPruebas.java
backend/src/test/java/pe/barberturno/common/tenancy/WhatsAppSalidaEsquemaPruebas.java
backend/src/test/java/pe/barberturno/conversation/RetencionWhatsAppIT.java
backend/src/test/java/pe/barberturno/reservations/AuditoriaAvisosIT.java
backend/src/test/java/pe/barberturno/whatsapp/AtencionHumanaWhatsAppIT.java
docs/v2/pruebas/v2-11-backend.md
```

Commits registrados:

- `7cc43d9`: producción y V116.
- `fa3040d`: pruebas de datos, migración y repositorios.
- `3d2bd9f`: inyección de la ventana, igualdad de caducidad y Javadoc.
- `6a79828`: compatibilidad del constructor y restauración de los fixtures existentes.

Pendiente: cierre de verificaciones y commits de ajustes, pruebas funcionales y evidencia.

### H-126-01 · Forma exacta del aviso en una prueba ajena

`backend/src/test/java/pe/barberturno/reservations/AuditoriaAvisosIT.java:106`, los dos casos de
`cp18_crearConfirmarCancelar_historialYDestinatariosExactos`, comprueban `contenido.get(i).size() == 6`.
El contrato de V2-11 §6 exige siete campos, incluido `conversacionId` nulo en un aviso de reserva. Ambos
casos llegan a esa comparación tras comprobar el historial y los valores del primer aviso; la aserción
interrumpe las comprobaciones restantes del caso y no se presenta como una prueba aprobada. Salida:

```text
Tests run: 44, Failures: 2, Errors: 0, Skipped: 0 -- in pe.barberturno.reservations.AuditoriaAvisosIT
cp18_crearConfirmarCancelar_historialYDestinatariosExactos(boolean)[1]
expected: 6
 but was: 7
cp18_crearConfirmarCancelar_historialYDestinatariosExactos(boolean)[2]
expected: 6
 but was: 7
```

Resuelto con autorización expresa del encargo 126b: se exige tamaño siete y `conversacionId` nulo;
se mantienen todas las comprobaciones de destinatarios, historial, reserva, mensaje y fecha.

Se conservan las firmas originales de los constructores utilizados por los fixtures existentes del enviador:
las adaptaciones intermedias de `EnviadorMensajesWhatsAppIT` y `PresupuestoEnvioWhatsAppIT` se revierten;
producción inyecta la ventana y la sobrecarga de compatibilidad usa la misma política. Su diff final respecto a la base es cero.

Incidencias de entorno: Git rechazaba la propiedad del worktree en el sandbox; se ejecutó con escalación
autorizada, sin cambiar ACL, propietarios, `safe.directory` ni configuración. El ejecutor serializado falló
inicialmente al abrir `bd-v2-test.lock` (`Access ... is denied`); se repitió con escalación y conservando el
cerrojo. `rg` no está disponible y el alias `python.exe` de WindowsApps es inaccesible: las lecturas usan
PowerShell y las ediciones `apply_patch`/UTF-8 explícito. No se instalaron herramientas ni dependencias nuevas.
