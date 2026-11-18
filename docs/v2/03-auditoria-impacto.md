# BarberTurno v2 · 03 · Auditoría de impacto sobre v1

> 21/10/2026. Base auditada: `main` = `efd52aa` (código idéntico a `3bcd5d0`, la entrega académica). Auditoría de código en **solo lectura** hecha por Codex (encargo 075, 0 pruebas ejecutadas, sin cambios) y contrastada por el coordinador con la arquitectura de v1. Referencias: `B/` = `backend/src/main/java/pe/barberturno/`, `T/` = `backend/src/test/java/pe/barberturno/`, `V1` = `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`.

## 1. Conclusión
La base compartida con `negocio_id` + RLS es viable sin reescribir el núcleo: **disponibilidad, reglas temporales, máquina de estados y protocolo de bloqueos se conservan** si la web y WhatsApp entran por los mismos servicios. El impacto grande está en tres zonas: **identidad y permisos** (un rol global único), **integridad entre tablas** (FK de una sola columna) y **contexto transaccional** (no existe ninguna infraestructura de negocio). No hay tareas en segundo plano, cachés ni JDBC de negocio que complique la migración.

## 2. Tablas
| Tabla | Hoy | v2 |
|---|---|---|
| `usuario` | Identidad + rol único (`CLIENTE/BARBERO/ADMIN`) + datos de cliente (`usuario_cliente_completo`, `V1:23`) | **Identidad global** (correo único global). El rol pasa a `rol_plataforma` + membresías; los datos de cliente, a la ficha `cliente`. |
| `servicio` | `servicio_nombre_uk` global (`V1:35`) | `negocio_id`; nombre único por negocio; `UNIQUE (negocio_id, id)`. |
| `barbero` | `usuario_id UNIQUE` (`V1:39`): una persona, un perfil | `negocio_id`; `UNIQUE (negocio_id, usuario_id)`; `usuario_id` opcional hasta aceptar la invitación; `nombre_visible` local. |
| `jornada`, `bloqueo` | Heredan del barbero por FK simple | `negocio_id` directo + FK compuesta al barbero (RLS uniforme). |
| `reserva` | FK simples a `usuario`, `barbero`, `servicio` (`V1:70`): una reserva podría mezclar un servicio del negocio A con un barbero del B | `negocio_id` + **FK compuestas** a servicio, barbero y ficha de cliente. `EXCLUDE` sin cambios (ver §4). |
| `auditoria_reserva`, `notificacion` | FK simple a la reserva | `negocio_id` + FK compuesta a la reserva. |
| `flyway_schema_history` | — | Sin cambios. |

Coste JPA: **medio/alto sin pasar a PK compuestas**. Se mantiene `Long id` con `IDENTITY`; se añade `negocioId` y `@JoinColumns` donde haga falta, evitando dos mapeos escribibles de `negocio_id`. Mitigación en la primera tarea: columna con `DEFAULT app_negocio_actual()` y entidades sin tocar (`04-arquitectura-datos.md` §3.4).

## 3. Consultas
Inventario: 8 interfaces Spring Data (41 métodos), 1 repositorio Criteria (`ReporteRepository`, 3 agregaciones), 2 consultas nativas (los *advisory locks*), ningún JDBC de negocio en producción.

| Clase | Significado | Ejemplos (archivo:línea) |
|---|---|---|
| **G** — global, filtraría datos entre negocios | Necesita filtro de negocio obligatorio (y RLS) | `ServicioRepository.listar` `:23` y `buscarPorNombre` `:31`; `BarberoRepository.countByActivoTrue` `:30`, `listar` `:37`, `findByActivoTrueOrderByIdAsc` `:70` (disponibilidad «cualquier barbero»); `ReservaRepository.findAll(Specification)` `:51`; `NotificacionRepository.listarPropias` `:24`, `contarNoLeidas` `:33`, `marcarTodasLeidas` `:50`; `ReporteRepository.porEstado/porServicio/porBarbero` `:34–:65`; `UsuarioAdminService` `findAll(spec)` `:64` |
| **I\*** — acotada por un id, **solo si antes se valida que pertenece al negocio** | Filtro explícito + RLS | Todas las búsquedas por id de servicio, barbero, jornada, bloqueo y reserva (`ReservaRepository :30–:190`, `JornadaRepository`, `BloqueoRepository`, `AuditoriaRepository :19`), y los `existsById`/`findById` heredados de `ReservaService :86, :95`, `DisponibilidadService :84, :135, :195`, `JornadaService :62`, `BloqueoService :65, :116`, `ReporteService :92, :95` (nombres de filtros vacíos en el Excel) |
| **P** — identidad global | Se mantiene | `UsuarioRepository.findByCorreo :22`, `bloquearPorCorreo :37`, `bloquearPorId :47` (login, perfil, revalidación) |
| **L** — bloqueo | Cambiar ámbito | *Advisory* `141414` (`BarberoRepository :23`) y `313131` (`UsuarioRepository :54`) |

Los métodos heredados de `JpaRepository` siguen disponibles aunque hoy no se usen: **filtrar los métodos propios no protege esos accesos**. Por eso RLS es necesaria como segunda barrera y no basta con revisar repositorios.

## 4. Concurrencia
| Nivel | Hoy | v2 |
|---|---|---|
| ⓪ cupo de barberos | `pg_advisory_xact_lock(141414)` global; también al desactivar (`BarberoService :119`) | Clave de dos partes `(141414, negocio_id)`; cupo contado por negocio. |
| ⓪ último ADMIN | `313131` global (DA-23) | Se conserva global para el último `ADMIN_PLATAFORMA`; `(313131, negocio_id)` para el último propietario. |
| ① cliente | Fila `usuario` (RN-04, RN-20 globales) | Fila de la **ficha** `cliente`: las reglas pasan a ser por negocio (V2-RN-08). |
| ② barberos | Ids ascendentes | Igual, validando que todos son del negocio; sin lotes ni reprogramaciones entre negocios. |
| ③ reserva | `FOR UPDATE` por id | Igual, buscando dentro del negocio antes de bloquear. |

Observaciones: (a) el `EXCLUDE` del barbero separa perfiles distintos; **no impide** que una persona con perfiles en dos negocios tenga citas simultáneas en ambos. Se acepta: cada negocio gestiona su agenda y comprobarlo entre negocios revelaría datos de uno a otro. (b) RN-20 solo se cuenta al crear (`ReservaService :109`), no al reprogramar (`:228`); se conserva.

## 5. Seguridad y sesión
- JWT: `sub`, `rol`, `tv`, `iat`, `exp` (`B/auth/JwtService :39`). **El claim `rol` no autoriza**: `UsuarioJwtConverter :37` relee el rol de la base en cada petición. Esa revalidación por petición es el punto natural para cargar la membresía del negocio de la ruta.
- Un único rol en `UsuarioAutenticado :15`, `UsuarioActual :12`, `SecurityConfig :145` (reglas por ruta), `ReservaAutorizacion :24, :36` y `ReservaDto :73` (permisos y teléfono).
- Superficie: **29 archivos Java de producción y 13 del frontend** referencian el rol (no todos cambian igual).
- Identificadores aceptados sin comprobar pertenencia (correcto en v1, de una sola barbería; a corregir en v2): `ReservaService :86, :210`; `DisponibilidadService :77, :84, :179`; `BarberoService :75, :138`; `JornadaService :76`; `BloqueoService :113, :126`; `UsuarioAdminService :81, :113`; `ReporteService :66, :92`.
- `ReservaService :88` exige `cliente.getRol() == CLIENTE` incluso en la reserva asistida: en v2 se sustituye por «existe la ficha del cliente en el negocio».

## 6. Arranque, configuración y segundo plano
- `AdminInicialRunner` → crea el `ADMIN_PLATAFORMA`; el propietario de cada negocio va por el alta de negocio.
- `DatosDemoService` (`:81, :96, :155`): idempotencia por correo global, cupo global y `findAll()` para elegir un ADMIN → escenario por negocio demo con membresías y fichas.
- `ParametrosReserva` (un bean global, `application.yml:43`) → parámetros por negocio en `negocio` (salvo `rejilla-min`, que sigue global en 10 porque el DDL exige duraciones múltiplos de 10).
- `TiempoNegocio` (zona fija Lima) y `ClockConfig` se conservan (S2-03).
- **No hay** `@Scheduled`, `@Async`, cachés ni ejecutores propios. El frontend sondea el contador de avisos cada 60 s (`avisos-service.ts:35, :48`).

## 7. Reportes, auditoría y avisos
- Las tres agregaciones comparten la `Specification` y `REPEATABLE_READ` (`ReporteService :28, :66`): añadir el negocio al criterio común conserva la conciliación con el historial. Filtrar solo la respuesta sería incorrecto.
- El Excel hace dos búsquedas extra para nombrar filtros sin resultados (`ReporteService :87`): también se acotan. La invariante de Guava acredita coherencia aritmética, **no aislamiento**.
- La auditoría se autoriza a través de la reserva y carga actores (`ReservaConsultaService :117`); sus JSON contienen ids sin FK y deben generarse desde entidades ya validadas.
- Avisos: consulta, contador, lectura individual y lectura masiva solo filtran por usuario (`NotificacionService :47, :63, :74, :85`). En v2 son por negocio; una bandeja que reúna varios negocios sería una función explícita (aplazada).

## 8. Frontend
Rutas sin negocio (`app.routes.ts:49–68`), guards por rol único (`guards.ts:21`), `SesionService` con un rol (`sesion-service.ts:16, :49`), modelos con un rol (`identidad.ts`, `usuarios.ts`), menú `switch` de tres roles (`shell.ts:31, :115`), reserva asistida y agenda basadas en `ADMIN` (`reserva.store.ts:50`, `agenda.ts:91`, `selector-cliente.ts:54`), `sessionStorage` con clave única sin negocio (`reserva.store.ts:17, :294`), sondeo de avisos por usuario (`avisos-service.ts:35`) y textos fijos («Una sede en Huamanga», política de 2 h; `shell.html:12, :160`, `privacidad.ts:11`, `reservar.html:285`). Al cambiar de negocio hay que **cancelar peticiones en curso y vaciar todo el estado** (catálogos, selección, franjas, filtros, diálogos), no solo cambiar el nombre en la cabecera.

## 9. Pruebas
- 33 clases `*IT`; **26 crean datos de dominio**. Base común `ReservaPruebaBase` (14 herederas); fábrica `DatosPrueba`; limpieza `LimpiezaBaseDatos` (8 tablas, solo `barberturno_test`); `EsquemaIT` y `DatosDemoRunnerIT` exigen **exactamente 8 tablas** (`EsquemaIT :55`) → se actualizan al añadir tablas, sin debilitarlas.
- Fábricas propias en `CorridaManualIT :56`, `AuthIT :601`, `BarberoIT :384`: no basta con tocar `DatosPrueba`.
- Dependen del rol único: `AuthIT :352, :378`, `BarberoIT :125`, `ReservaAsistidaIT :62`, `PoliticaTransicionesTest :19` (`Rol.values()`), `UsuarioAdminIT :68`. Se trasladan a rol de negocio o de plataforma conservando la cobertura.
- `ConcurrenciaReservaIT` usa hilos propios: el contexto de negocio **no se propaga solo** a `TransactionTemplate` ni a ejecutores; cada hilo lo fija.

## 10. Riesgos principales y respuesta del diseño
| # | Riesgo (auditoría 075) | Respuesta en v2 |
|---|---|---|
| 1 | Permisos globales reutilizados como locales: un `ADMIN` o un id válido alcanza recursos de otro negocio | Rol de plataforma sin permisos de negocio (V2-RN-03); negocio autorizado por membresía en cada petición (V2-RN-02); RLS; pruebas de dos negocios en cada tarea (V2-RNF-01). |
| 2 | RLS ineficaz o contexto contaminado (rol propietario o con *bypass*, conexión reutilizada, consultas antes del contexto) | Dos roles (propietario/aplicación), `FORCE`, `set_config(..., true)` al empezar cada transacción, función que falla cerrada, prueba con *pool* de 1 conexión (V2-01). |
| 3 | Integridad cruzada sin FK compuestas | `UNIQUE (negocio_id, id)` + FK compuestas en todos los hijos (V2-01), probadas con inserciones cruzadas. |
| 4 | Control local sobre identidades globales (restablecer contraseñas, editar o desactivar cuentas de personas que están en varios negocios) | **Nueva regla V2-RN-16**: el negocio solo actúa sobre lo suyo; la plataforma restablece cuentas compartidas; desactivar un profesional no desactiva su cuenta. |
| 5 | Reglas de concurrencia con ámbitos distintos (conteos locales con exclusiones globales o al revés) | Todos los ámbitos fijados por negocio y documentados (§4, V2-RN-08); el bloqueo ① pasa a la ficha a la vez que RN-04 y RN-20. |

Supuestos de la propuesta de partida que el código no sostenía y que el diseño ya corrige: usuario ≠ membresía (V2-DA-03); un usuario podía tener solo un perfil de barbero (ahora uno por negocio); reglas de cliente globales (ahora por ficha); la resolución por membresía no cubre la navegación anónima ni WhatsApp (ruta pública por `slug` y negocio por `phone_number_id`, V2-RN-02).
