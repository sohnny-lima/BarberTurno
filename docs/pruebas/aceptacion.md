# Aceptación de BarberTurno · T-37

Fecha de ejecución: **04/10/2026 (America/Lima)**. Versión verificada: `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39`. Base: `ead20a16460c1c5930a1fafaba23db6942c8d494`, con T-34, T-36, T-38, T-45 y T-46 integradas. La diferencia entre ambos commits es solo el estado documental de T-37. No se cambió código durante las verificaciones.

Este registro aplica requisitos §9, RA-02 informativa y RA-06. CP-01…CP-12 conservan las entradas del informe APF2 §15; CP-13…CP-19 siguen requisitos §9.1. **18 casos con evidencia técnica; CP-11 pendiente de ejecución humana por el responsable (P-07)**. El cierre documental de T-37 admite esa pendiente explícita según el encargo 040; no declara ejecutada la sesión con cinco personas ni valida producción.

## Verificaciones del HEAD

| Comando | Resultado del HEAD |
|---|---|
| Backend: `mvnw.cmd -B -ntp verify` | 1535/1535, 52 clases, 0 fallos/errores/omisiones; 08:12 min; JaCoCo aprobado, Javadoc sin avisos. |
| Frontend: `npm run lint` | Lint y tipos E2E aprobados. |
| Frontend: `npm test -- --watch=false` | 317/317, 40 archivos; 81,21 s. |
| Frontend: `npm run build` | Aprobado, 489,88/500 kB iniciales; 20,671 s. |
| E2E: `npm run e2e` | 28/28, cuatro proyectos; 4,2 min; 0 fallos/omisiones/flaky/reintentos; 44 análisis axe sin violaciones. |
| Reserva automatizada (cuatro proyectos) | 3 pasos, 4281–7109 ms (4,28–7,11 s); evidencia parcial RNF-06. |

Firefox no reprodujo el 403 residual en esta ejecución. Los avisos JVM CDS y colores NO_COLOR/FORCE_COLOR son informativos y se conservan en t-37.md. El empaquetado E2E pasó (03:07 min) después de verify.

Los nombres siguientes se comprobaron en XML Surefire, con todas sus invocaciones sin fallo, error ni omisión. La extracción agregada y las evidencias seleccionadas están en [t-37-resultados.json](t-37-resultados.json); los informes completos se generaron en `backend/target/surefire-reports/`. Se extrajeron antes de `npm run e2e`, cuyo empaquetado limpio retira ese directorio y JaCoCo. El [informe E2E autónomo](e2e/index.html) y [t-37.md](t-37.md) conservan la comprobación de escenarios Playwright. Las referencias de carga y recuperación conservan sus fechas y commits originales: no se ejecutaron esas cargas ni el ensayo de recuperación de nuevo en T-37.

## Casos CP-01…CP-19

### CP-01 · Registro y perfil

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Nombre/correo/teléfono ficticios válidos, consentimiento y contraseña RN-25; correo repetido con otras mayúsculas; campos inválidos; edición de nombre y teléfono. |
| Resultado esperado | Cuenta CLIENTE y sesión; inválidos 400 y duplicado 409; cambios permitidos persisten sin editar correo. |
| Resultado observado | Cuenta y consentimiento persistidos, hash BCrypt y claims comprobados; duplicados/invalidaciones rechazados; perfil persiste después de recargar. |
| Evidencia | `AuthIT#registro_valido_normalizaConsentimientoHashYClaimsSinExponerDatos`, `AuthIT#registro_correoConOtrasMayusculas_devuelve409`, `AuthIT#registro_datosInvalidos_devuelve400SinCrearUsuario`, `PerfilIT#perfil_obtenerYActualizar_persisteSoloLosCamposEditables`; Playwright escenario 07. |

### CP-02 · Reserva ajena y autorización

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | CLIENTE consulta reserva de otro cliente; Carlos consulta la de Miguel; ADMIN filtra agenda; cliente intenta administración y vuelve tras logout. |
| Resultado esperado | Reserva ajena 404 uniforme sin revelar datos; solo propias/asignadas salvo ADMIN; rutas protegidas exigen sesión. |
| Resultado observado | Propiedad, asignación, filtro forzado y 404 uniformes pasan; UI redirige por rol y tras logout. |
| Evidencia | `ReservaConsultaIT#cp02_clienteSoloPropiasY404Uniforme`, `ReservaConsultaIT#cp02_carlosNoVeMiguelYFiltroForzado`, `ReservaConsultaIT#cp02_adminVeTodasYFiltraDimensionesYCombinacion`; Playwright escenario 05. |

### CP-03 · Concurrencia

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Diez clientes disputan la misma franja, veinte repeticiones; dos contextos de navegador confirman simultáneamente. |
| Resultado esperado | Exactamente 1 respuesta 201 y 9 conflictos 409 en cada repetición; navegador perdedor informa conflicto y recarga. |
| Resultado observado | Las veinte repeticiones pasan; en cada proyecto Playwright se observa 201/409 y desaparece la franja del perdedor. |
| Evidencia | `ConcurrenciaReservaIT#cp03_diezClientesMismaFranja_unExitoYNueveConflictos`; Playwright escenario 06. |

### CP-04 · Contigüidad

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Dos reservas del mismo cliente/barbero de 10:00–10:30 y 10:30–11:00; E2E reserva 11:30 después del ganador de 11:00. |
| Resultado esperado | Ambas se aceptan; intervalos semiabiertos permiten inicio igual al fin anterior. |
| Resultado observado | Dos 201 y dos filas; consulta publica la siguiente franja contigua y E2E confirma 201. |
| Evidencia | `ReservaCrearIT#cp04_contiguas_ambas201`, `DisponibilidadIT#corrida_publicaSinSesion_intervalosCompletosContiguosYDesfaseLima`; Playwright escenario 06. |

### CP-05 · Reprogramación atómica

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Reprogramar una CONFIRMADA a franja ocupada; luego a franja libre con catálogo modificado. |
| Resultado esperado | Ocupada 409 y cita original íntegra; libre conserva código, precio/duración de referencia y servicio. |
| Resultado observado | La prueba compara fila completa, versión, referencias y auditoría sin cambios al fallar; libre conserva referencias y UI muestra nueva hora. |
| Evidencia | `ReprogramacionIT#cp05_franjaOcupada_conservaFilaCompletaVersionReferenciasYAuditoria`, `ReprogramacionIT#cp05_franjaLibre_conservaPrecioDuracionYServicioAunqueCatalogoCambieYSeDesactive`; Playwright escenario 01. |

### CP-06 · Cancelación y límite de dos horas

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Cita 10:00; reloj 07:00, 08:00, 08:01, 10:00 y 10:01; ADMIN antes del inicio con motivo suficiente/insuficiente; BT-104 a las 09:00. |
| Resultado esperado | Cliente permitido con ≥ 2 h; fuera de política 422; ADMIN requiere motivo y la excepción se audita; al inicio ya no cancela. |
| Resultado observado | Límite exacto incluido, 1 h 59 min rechazado; excepción ADMIN y motivo comprobados; UI oculta acciones de BT-104 y muestra ayuda. |
| Evidencia | `CancelacionIT#cp06_ventanaCliente_limiteExactoPermitido`, `CancelacionIT#adminConMotivo_registraExcepcionSoloFueraDeVentanaCliente`, `CancelacionIT#adminATreintaMinutos_motivoInsuficiente422`; Playwright escenarios 01 y 02. |

### CP-07 · Cambios de disponibilidad con citas

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Jornada que deja fuera BT-101; bloqueo Carlos 01/10 10:10–10:20 que cruza cita; franja libre y bloqueo en lote. |
| Resultado esperado | Cambio incompatible 409 y disponibilidad anterior intacta; libre aceptada; lote atómico. |
| Resultado observado | Conflicto con identificación pública de cita y sin cambios; bloqueo libre creado; lote con un conflicto no crea ninguno. |
| Evidencia | `JornadaIT#cp07_bt101NoCabeEntera_conflictoConIdSinCambios`, `BloqueoIT#cp07_bt101CruzaBloqueo_conflictoConId_yFranjaLibreCrea`, `BloqueoIT#lote_conflictoEnUnPerfil_noCreaNinguno_yAgrupaTodosLosConflictos`, `BloqueoIT#lote_sinConflictos_creaTodos_bloqueaUnaVezEnOrdenAntesDeConsultar`; Playwright escenario 04. |

### CP-08 · Desactivación sin pérdida de historial

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Editar y desactivar servicio con cita; desactivar/reactivar barbero con reservas de varios estados. |
| Resultado esperado | Citas e historial íntegros, solo activos en selección pública; acceso del barbero desactivado revocado. |
| Resultado observado | Fila de reserva y precio/duración originales conservados; contador de futuras correcto; catálogo público excluye inactivos y sesión revocada. |
| Evidencia | `ServicioIT#cp08_editarPrecioYDuracionYDesactivar_conservaTodaLaReservaExistente`, `BarberoIT#cp08_reservasFuturasVigentes_contadorCorrectoYFilasIntactas`, `BarberoIT#editar_desactivarYReactivar_conservaCuentaEHistorialYRevocaSesion`, `BarberoIT#listado_contactosEInactivosSoloAdmin_ordenadoPorNombre`. |

### CP-09 · Estados, versión y auditoría

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Flujo CONFIRMADA→EN_ATENCION→COMPLETADA; saltos/repeticiones; versión 0 tras actualización a 1; lectura de auditoría por rol. |
| Resultado esperado | Solo transiciones válidas; 409 TRANSICION_INVALIDA o VERSION_DESACTUALIZADA; autor y avisos registrados, lectura autorizada. |
| Resultado observado | Flujo válido con tres auditorías/avisos; saltos y repetición rechazados; versión antigua se detecta antes de la política temporal; permisos de auditoría uniformes. |
| Evidencia | `TransicionesIT#cp09_flujoCompleto_asignadoYAdminTresAuditoriasYAvisos`, `TransicionesIT#saltarEstadoORepetir_409`, `TransicionesIT#cp09_versionAntigua_precedeAPoliticaTemporal`, `AuditoriaAvisosIT#auditoria_permisosUniformes`; Playwright escenario 03. |

### CP-10 · Reportes conciliados

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Periodo y filtros de barbero/servicio/estado en cuatro combinaciones; E2E 27/09–01/10. |
| Resultado esperado | Estados suman total; agrupaciones coinciden con el historial bajo los mismos filtros. |
| Resultado observado | Cuatro combinaciones conciliadas; E2E siete reservas, estados 0/4/0/2/0/1 y agrupaciones 3/4 por servicio, 1/6 por profesional. |
| Evidencia | `ReporteIT#cp10_conciliaTodasLasSumasYElHistorial`; Playwright escenario 04. |

### CP-11 · Usabilidad con cinco participantes

| Campo | Registro |
|---|---|
| Fecha / commit | Plantilla preparada 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39`; fecha y commit de sesión humana pendientes. |
| Entradas | Cinco participantes, 360 px y escritorio, reserva en ≤ 180 s y ≤ 5 pasos; tiempos, errores y finalización. |
| Resultado esperado | Medición real de cinco personas y controles utilizables en ambos tamaños. |
| Resultado observado | **Pendiente de ejecución por el responsable (P-07)**. E2E confirma tres pasos y controles sin desbordamiento, con tiempos automáticos; no hay participantes ni resultado humano. |
| Evidencia | [Plantilla con consentimiento, guion y tabla P1…P5](usabilidad-plantilla.md). Evidencia técnica parcial: Playwright escenarios 01 y 07, cuatro proyectos y análisis axe; no constituye aceptación humana de RNF-06. |

### CP-12 · Carga, recuperación, cobertura y seguridad

| Campo | Registro |
|---|---|
| Fecha / commit | Seguridad/cobertura: 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39`. Carga T-35/T-43 y recuperación T-36: 04/10/2026, commits originales en sus informes enlazados. |
| Entradas | Carga 50 usuarios/300 s; dump/restauración de demo de 9 tablas/56 filas; pruebas JWT, BCrypt, CSRF, roles, cabeceras, errores y JaCoCo. |
| Resultado esperado | p95 disponibilidad ≤ 2 s; restauración ≤ 4 h; seguridad del servidor y umbral ≥ 70 % en dominio. |
| Resultado observado | Carga T-35 y T-43 p95 11 ms; T-43 disponibilidad 0/14.363 errores y global 23/19.766 KO (0,116361 %), todos los KO siguen computándose. T-36 respaldo 1,442 s, recuperación/comparación 3,389 s y ensayo ampliado 14,543 s; 9 tablas/56 filas exactas incluso sin origen. Seguridad y cobertura del HEAD pasan. Producción y operación diaria requieren las acciones pendientes de la matriz. |
| Evidencia | [Carga T-35](carga/informe-t-35.md), [clasificación T-43](carga/informe-t-43.md), [recuperación](recuperacion.md), [T-36](t-36.md). `AuthIT#registro_valido_normalizaConsentimientoHashYClaimsSinExponerDatos`, `ReservaAsistidaIT#adminConSesion_csrfAusenteOIncorrecto403`, `CabecerasSeguridadIT#responder_enHttps_imponeHstsYCsp`, `AuthIT#recuperacion_cookieRevocada_permiteLoginRegistroYBorraEn401SinAceptarToken`, `ErroresMvcIT#reservar_conSesionClienteCsrfYTexto_devuelve415ValidacionSinEscrituras`, `ErroresMvcIT#health_conAcceptHtml_devuelve406SinCuerpoNiTipo`, `ManejadorErroresTest#metodoNoPermitido_enMvc_devuelve405ConProblemaHabitual`; cobertura más abajo. |

### CP-13 · Solape del cliente entre barberos

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Mismo cliente a las 10:00 con dos barberos distintos. |
| Resultado esperado | Segunda reserva 409 CLIENTE_CON_RESERVA_SOLAPADA. |
| Resultado observado | Primera creada y segunda rechazada con el código previsto. |
| Evidencia | `ReservaCrearIT#cp13_clienteConDosBarberos_409`. |

### CP-14 · Bloqueo temporal de acceso

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Cinco contraseñas incorrectas; contraseña correcta antes de 15 minutos y exactamente al límite. |
| Resultado esperado | 401 con error genérico y bloqueo temporal de 15 min; después acceso permitido. |
| Resultado observado | Contador 5 y bloqueo persistidos; a 14 min 59 s sigue rechazado; a 15 min responde 200 y reinicia contador/bloqueo. |
| Evidencia | `AuthIT#login_cincoFallos_bloqueaQuinceMinutosYDesbloqueaEnElLimite`. |

### CP-15 · Máximo de reservas futuras

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Cliente reserva 10:00, 11:00 y 12:00; intenta 13:00. |
| Resultado esperado | Cuarta futura 422 LIMITE_RESERVAS_ACTIVAS. |
| Resultado observado | Tres reservas creadas y cuarta rechazada con el código previsto. |
| Evidencia | `ReservaCrearIT#cp15_cuartaFutura_422`. |

### CP-16 · Jornada inválida

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Semana con intervalos solapados, iguales o inicio posterior al fin y otros cuerpos inválidos. |
| Resultado esperado | 400 JORNADA_INVALIDA, jornada anterior conservada. |
| Resultado observado | Errores con índice de intervalo y filas anteriores íntegras en todas las variantes. |
| Evidencia | `JornadaIT#cp16_semanaInvalida_devuelve400ConIndiceYConservaFilas`. |

### CP-17 · Cambio y restablecimiento de contraseña

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Dos cookies/sesiones existentes; cambio por usuario y restablecimiento ADMIN con contraseña temporal. |
| Resultado esperado | Sesiones previas 401 en siguiente petición; cambio propio renueva sesión actual; temporal exige cambio. |
| Resultado observado | Ambas cookies antiguas revocadas por cambio; restablecimiento revoca, desbloquea acceso y exige cambio; recuperación DA-22 no acepta JWT anterior. |
| Evidencia | `PerfilIT#cp17_cambioValido_revocaAmbasCookiesAntiguasYRenuevaLaSesionActual`, `UsuarioAdminIT#cp17_temporalRevocaSesionAnterior_exigeCambioYDesbloqueaAcceso`, `AuthIT#recuperacion_cookieRevocada_permiteLoginRegistroYBorraEn401SinAceptarToken`. |

### CP-18 · Avisos propios

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | Crear, confirmar como ADMIN/barbero y cancelar; consultar y marcar avisos por cada rol. |
| Resultado esperado | Destinatarios exactos según RN-15; cada usuario solo lee los suyos, sin duplicar la lectura. |
| Resultado observado | Historial y destinatarios exactos en ambas variantes de confirmación; contador/lectura propios e idempotencia comprobados. |
| Evidencia | `AuditoriaAvisosIT#cp18_crearConfirmarCancelar_historialYDestinatariosExactos`, `AuditoriaAvisosIT#avisos_cadaRolLeeSoloLosSuyosYMarcaIdempotentemente`; Playwright escenario 01. |

### CP-19 · Reserva asistida

| Campo | Registro |
|---|---|
| Fecha / commit | 04/10/2026 · `94dbe992217151ea6a2bc7b4c33a359ebf1bcd39` |
| Entradas | ADMIN reserva para CLIENTE en franja ocupada y después libre, con sesión y CSRF reales. |
| Resultado esperado | Primero 409, después 201; ADMIN creador/actor en auditoría y avisos apropiados. |
| Resultado observado | Conflicto y creación exactos; actor ADMIN, reserva CONFIRMADA y destinatarios comprobados. |
| Evidencia | `ReservaAsistidaIT#cp19_ocupada409_libre201_conActorAdminYAvisos`. |

## Matriz de trazabilidad completa

Las filas siguientes cubren RF-01…RF-21 de requisitos §9 y todos los RNF de §3, incluidos RNF-02/13/14 omitidos de la tabla abreviada de §9. «Parcial» conserva una evidencia ejecutada y especifica la aceptación externa pendiente.

| Requisito | Evidencia | Estado y límite |
|---|---|---|
| RF-01 | CP-01 · AuthIT y E2E 07 | Cubierto. |
| RF-02 | CP-02, CP-14, CP-17 · AuthIT y E2E 05 | Cubierto. |
| RF-03 | CP-01 · PerfilIT y E2E 07 | Cubierto. |
| RF-04 | CP-08 · ServicioIT; E2E 04 | Cubierto. |
| RF-05 | CP-08 · BarberoIT | Cubierto. |
| RF-06 | CP-07, CP-16 · JornadaIT/BloqueoIT; E2E 04 | Cubierto. |
| RF-07 | CP-04; `DisponibilidadIT#validacion_todaCandidataDeRejillaCoincideConConsulta_yClasificaCodigo` | Cubierto. |
| RF-08 | CP-03, CP-04, CP-13, CP-15 · ReservaCrearIT/ConcurrenciaReservaIT; E2E 01/06 | Cubierto. |
| RF-09 | CP-05 · ReprogramacionIT; E2E 01 | Cubierto. |
| RF-10 | CP-06 · CancelacionIT; E2E 01/02 | Cubierto. |
| RF-11 | CP-02 · ReservaConsultaIT; E2E 03 | Cubierto. |
| RF-12 | CP-09 · TransicionesIT; E2E 03 | Cubierto. |
| RF-13 | CP-02, CP-10 · ReservaConsultaIT/ReporteIT; E2E 01/04 | Cubierto. |
| RF-14 | CP-10 · ReporteIT; E2E 04 | Cubierto. |
| RF-15 | CP-17 · PerfilIT | Cubierto. |
| RF-16 | CP-18 · AuditoriaAvisosIT; E2E 01 | Cubierto. |
| RF-17 | CP-09 · AuditoriaAvisosIT | Cubierto. |
| RF-18 | CP-19 · ReservaAsistidaIT | Cubierto. |
| RF-19 | CP-17; `UsuarioAdminIT#buscaNombreCorreoSinMayusculas_filtraRolPaginaYSinDatosInternos`, `UsuarioAdminIT#ultimoAdmin_yPropiaCuenta_noSeDesactivan409` | Cubierto. |
| RF-20 (opcional) | CP-07 · BloqueoIT lote | Implementado y cubierto. |
| RF-21 (opcional) | `DisponibilidadIT#rf21_fusionaPerfilesOrdenados_omiteInactivosYNoTieneNmasUno` | Implementado y cubierto. |
| RNF-01 | CP-12 · carga T-35/T-43 | Cumple en equipo local/volumen/modelo documentados; histórico, no carga nueva del HEAD. |
| RNF-02 | [T-33](t-33.md), `/actuator/health` probado | Pendiente 99 % mensual tras alojamiento/monitor externo, P-03; no se puede medir sin despliegue. |
| RNF-03 | CP-12 · AuthIT, CabecerasSeguridadIT; [T-33](t-33.md), [T-45](t-45.md) | BCrypt/JWT/cookies/cabeceras cubiertos; HTTPS real y certificado pendientes P-03. |
| RNF-04 | CP-02 y CP-12 · roles, propiedad y asignación en API | Cubierto. |
| RNF-05 | CP-03, CP-05, CP-13 · concurrencia/PostgreSQL real | Cubierto. |
| RNF-06 | CP-11 · E2E 01 parcial y [plantilla](usabilidad-plantilla.md) | Pendiente cinco participantes P-07; tres pasos/tiempo automatizado no sustituyen personas. |
| RNF-07 | [E2E actual](e2e/index.html) y [T-34](t-34.md) | Parcial: Chromium/Firefox de Playwright; pendiente matriz de dos últimas versiones comerciales Chrome, Edge y Firefox. |
| RNF-08 | E2E cuatro proyectos 360/1440 px | Cubierto en esos tamaños; comprobación de ancho global en escenarios axe. |
| RNF-09 | CP-12 · [recuperación](recuperacion.md), [T-36](t-36.md) | Ensayo real con/sin origen y retención 33/33 acreditados; programación diaria/vigilancia y segunda ubicación pendientes de operación P-03. RPO 24 h exige comprobar antigüedad diaria en el despliegue. |
| RNF-10 | JaCoCo del HEAD, umbral verify | Cubierto; líneas de ambos paquetes > 70 %. CI remota pendiente P-04. |
| RNF-11 | E2E · 44 análisis axe y foco/teclado | Evidencia básica sin violaciones; no equivale a auditoría integral WCAG. |
| RNF-12 | CP-01, CP-02; `ReservaConsultaIT#telefonoSoloPersonalYDatosMinimos` | Consentimiento y minimización/aislamiento comprobados; evidencia funcional, sin dictamen legal. |
| RNF-13 | CP-04/06; E2E Europe/Madrid muestra Lima; `ReservaConsultaIT#agendaSemanalLunesDomingoYFronteraLima` | Cubierto con Clock fijo y otra zona de navegador. |
| RNF-14 | [T-33](t-33.md) logs ECS sanitizados/health; `ManejadorErroresTest#clienteMvc_conEstado4xx_registraWarnSinTrazaNiDatos` y `ErroresMvcIT#health_conAcceptHtml_devuelve406SinCuerpoNiTipo` | Salud y errores actuales probados; revisión de logs estructurados de T-33 histórica. |

## Seguridad consolidada (T-33, T-45 y T-46)

T-33 prepara `prod`, SPA pública sin abrir API, cookies y CSP/HSTS/nosniff/Referrer-Policy/DENY, Swagger deshabilitado y logs ECS sanitizados. T-45 ignora la cookie solo en login/registro/logout, permite recuperación tras revocación y borra BT_SESION en 401 manteniendo sus atributos; nunca rehabilita el token anterior. T-46 conserva 405/415 con VALIDACION y 406 sin cuerpo; el fallback 500 sigue genérico. Sus [revisiones T-33](revision-t-33.md), [T-45](revision-t-45.md) y [T-46](revision-t-46.md) están aprobadas; la observación de errores MVC de T-33 está resuelta por T-46.

La ejecución actual incluye sesión válida sin CSRF → 403 y CSRF incorrecto → 403 para las escrituras existentes, junto con roles no autorizados; no se crearon endpoints. Ejemplos comprobados: `CancelacionIT#sesionValidaSinCsrf_403ClienteYAdmin`, `CancelacionIT#sesionValidaCsrfIncorrecto_403ClienteYAdmin`, `ReprogramacionIT#sesionValidaSinCsrfOIncorrecto403_clienteYAdmin`, `TransicionesIT#sesionValidaSinCsrfOIncorrecto_403SinEscrituras`, `UsuarioAdminIT#escrituras_sesionValidaSinCsrfOCsrfIncorrecto403`, `UsuarioAdminIT#rolNoAutorizado403_enTodasLasRutas`, `ReservaCrearIT#rolNoAutorizado_403` y `AuthIT#recuperacion_sesionValidaORevocadaConCsrfInvalido_mantiene403`. Se conserva DEFAULT_CSRF_MATCHER. No se añaden Javadoc porque T-37 no modifica Java; verify comprueba el existente.

## Medición final de Java y cobertura

Java de producto: **4284 / 10643 LOC = 40,25 %**. Java con pruebas: **13324 / 24218 LOC = 55,02 %**. Pruebas: 13575 LOC, incluidas 345 LOC Java de carga (contadas una sola vez). Medición UTC 05/10/2026 03:37:15, equivalente a 04/10/2026 22:37:15 Lima, HEAD `94dbe99`.

RA-02 fue reclasificada por el responsable el 04/10/2026: porcentaje **informativo, sin umbral**. Método y exclusiones: [medición acumulada](medicion-java.md); `node tools/medir-java.mjs --escribir`, Node 24.21.0. La cabecera de ese documento se actualiza a la clasificación vigente, conservando los registros históricos. El medidor aún contiene una introducción antigua de ≥ 50 % para un documento nuevo; se registra como hallazgo, sin modificar código aquí. No se redistribuyó lógica para alterar LOC.

| Alcance | Cubiertas | Perdidas | Total | Cobertura de líneas |
|---|---:|---:|---:|---:|
| Backend total | 1778 | 19 | 1797 | 98,94 % |
| pe.barberturno.reservations | 356 | 0 | 356 | 100 % |
| pe.barberturno.scheduling | 397 | 2 | 399 | 99,50 % |

[CSV JaCoCo del verify](t-37-jacoco.csv). Se suman las líneas cubiertas y perdidas del informe XML/CSV; el total abarca todo backend/src/main/java. El dominio crítico usa exactamente los paquetes configurados por JaCoCo (`pe.barberturno.reservations` y `pe.barberturno.scheduling`), sin sumar sus subpaquetes DTO al umbral. No baja respecto de T-34/T-36/T-46.

## Riesgos residuales y pendientes del responsable

- 403 intermitente al cancelar en Firefox: observación O-1 de [revisión T-34](revision-t-34.md), inicialmente no reproducida; conservar seguimiento aunque la ejecución actual pase.
- ~~CI nunca ejecutada en remoto~~: **resuelto el 05/10/2026** (P-04): primer CI verde en GitHub Actions, ejecución [37314699514](https://github.com/sohnny-lima/BarberTurno/actions/runs/37314699514); el primer run destapó una dependencia de orden en las pruebas, corregida en T-47 ([ci-primer-run.md](ci-primer-run.md)).
- PostgreSQL local escucha en todas las interfaces por configuración del instalador; acceso limitado por `pg_hba.conf`. Decisión del responsable, sin cambiar configuración.
- Poco espacio libre en C:; navegadores/temporales Playwright se guardan en `frontend/tmp/` de D:.
- Bundle inicial cerca de presupuesto: aproximadamente 490 de 500 kB; cifra actual en la verificación.
- Horizonte de 30 días fijo en frontend: O-1 de revisión T-26; cambios futuros de parámetros requieren coherencia de UI.
- Base `barberturno_demo_t32` conservada con datos ficticios; demo idempotente conserva ediciones, no reinicia el guion automáticamente.
- P-07 (cinco participantes), P-03 (alojamiento/TLS/monitor/respaldo vigilado) y matriz comercial de RNF-07 pendientes. El ensayo local de recuperación no acredita por sí solo el RPO operativo ni pérdida completa del equipo.

Hallazgos editoriales para el arquitecto: requisitos §9 omite RNF-02/13/14 en su tabla abreviada; se incluyen aquí sin modificar el documento. La introducción del medidor usa RA-02/P-02 históricos aunque requisitos y arquitectura ya la reclasifican. El encargo inicial decía PostgreSQL 18.0/T-38 pendiente: la precisión posterior y T-38 prevalecen, se verifica sobre 18.6. No se requiere cambiar API, DDL o RN para cerrar T-37.
