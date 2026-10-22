// Genera APF3_BarberTurno_Informe.docx. Uso: node informe.js <salida.docx>
const fs = require('fs');
const path = require('path');
const L = require('./lib-docx');
const { d, p, h1, h1Continuo, h2, h3, vinetas, numerada, tabla, codigo, figura, figuraFila, notaBloque, runs, VERDE, GRIS, FUENTE } = L;
const { Document, Packer, Paragraph, TextRun, AlignmentType, Header, Footer, PageNumber, TableOfContents, LevelFormat, HeadingLevel, PageBreak } = d;

const S = path.resolve(__dirname, '..');
const APF3 = path.join(S, 'apf3');
const EV = path.join(APF3, 'evidencias');
const T58 = JSON.parse(fs.readFileSync(path.join(__dirname, 't58.json'), 'utf8'));
const CAP = T58.capturas; // carpeta con chromium/ de las capturas de la versión entregable
const cap = (n) => path.join(CAP, n + '.png');
const gh = (n) => path.join(EV, 'github', n + '.png');
const az = (n) => path.join(EV, 'azure', n + '.png');
const dg = (n) => path.join(EV, 'diagramas', n + '.png');
const REPO = 'https://github.com/sohnny-lima/BarberTurno';

const c = [];
const add = (...xs) => xs.flat().forEach((x) => c.push(x));

// ---------------- Portada
const centro = (t, o = {}) => new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: o.after ?? 120, before: o.before ?? 0 },
  children: [new TextRun({ text: t, font: FUENTE, size: o.size || 24, bold: o.bold, italics: o.italics, color: o.color })] });
add(
  centro('UNIVERSIDAD TECNOLÓGICA DEL PERÚ', { size: 26, before: 400 }),
  centro('Facultad de Ingeniería', { size: 24, after: 40 }),
  centro('Carrera de Ingeniería de Software', { size: 24, after: 900 }),
  centro('BarberTurno', { size: 64, bold: true, color: VERDE, after: 80 }),
  centro('Sistema web de reservas y turnos para barberías', { size: 28, italics: true, after: 700 }),
  centro('Avance de Proyecto Final 3 (AC-S12-APF3)', { size: 34, bold: true, after: 80 }),
  centro('Construcción del producto: arquitectura, recursos Java, control de versiones e interfaces', { size: 24, after: 900 }),
  centro('Estudiante', { size: 22, after: 20 }), centro('Sohnny Walter Lima Infanzón', { size: 24, bold: true, after: 300 }),
  centro('Curso Integrador I: Sistemas Software', { size: 22, after: 20 }), centro('Sección 20058 · Virtual en vivo', { size: 22, after: 300 }),
  centro('Docente', { size: 22, after: 20 }), centro('Mg. Ing. Juan Barahona Sánchez', { size: 24, after: 300 }),
  centro('Fecha límite de entrega: 29 de octubre de 2026, 22:30 (hora de Lima)', { size: 22, after: 20 }),
  centro(`Versión del informe: ${T58.fechaInforme}`, { size: 20, color: GRIS, after: 300 }),
  centro('Ayacucho · Perú · 2026', { size: 22 }),
);

// ---------------- Índice
add(new Paragraph({ pageBreakBefore: true, children: [new TextRun({ text: 'Índice', bold: true, size: 40, color: VERDE, font: FUENTE })], spacing: { after: 200 } }),
  new TableOfContents('Índice', { hyperlink: true, headingStyleRange: '1-2' }));

// ---------------- Resumen
add(h1('Resumen'));
add(p(`El tercer avance de **BarberTurno** demuestra la evolución desde el diseño y el prototipo del APF2 hasta un sistema implementado, probado y desplegado. Se conserva la alternativa seleccionada (**A1**): una SPA en **Angular 22**, una API **Spring Boot 4.1.1 con Java 21** que concentra toda la lógica de negocio y una base **PostgreSQL 18**. El alcance comprometido se mantiene: una barbería de una sede en Huamanga, hasta diez profesionales activos y tres roles (cliente, barbero y administrador).`));
add(p(`Los catorce requisitos funcionales del APF2 (RF-01 a RF-14) están implementados con interfaces que funcionan para los tres roles, junto con siete mejoras registradas (RF-15 a RF-21). El código está en un repositorio público de GitHub con ${T58.commitsMain} commits en \`main\`, integración continua con GitHub Actions y un historial trazable por tarea (T-01 a T-57). La versión del código \`d2f3107\` superó **1548** pruebas de backend, **442** de frontend y **48** casos E2E en el último checkpoint (17–18/10/2026), y está desplegada en **Azure for Students** desde el 19/10/2026.`));
add(p(`Para este avance se incorporó la tarea **T-58** (exportación del reporte a Excel), que acredita el uso real de **Apache POI**, **Apache Commons Lang** y **Google Guava**, además de **Logback**, que el sistema ya utilizaba. ${T58.resumenEstado}`));
add(p(`El informe distingue la **cobertura funcional implementada** de la **aceptación completa**: siguen pendientes la prueba de usabilidad con cinco participantes (CP-11), la medición mensual de disponibilidad (RNF-02), la prueba funcional mínima en producción y el ensayo de restauración en Azure. El negocio es un caso académico: no se presentan entrevistas, clientes reales ni beneficios económicos.`));
add(h2('Cómo leer este informe'));
add(tabla(['Criterio de la rúbrica SI53-APF3-2026A', 'Puntaje', 'Secciones'], [
  ['1. Diseño de la solución (MVC, TDD, DAO, SOLID y seguridad)', '3', '3, 4, 5 y 6'],
  ['2. Uso de recursos Java (Guava, POI, Commons, Logback)', '2', '7'],
  ['3. Control de versiones (Git y GitHub)', '3', '10'],
  ['4. Implementación de las interfaces gráficas', '6', '8 y 9'],
  ['5. Construcción del producto final', '4', '9, 11, 12, 13 y 14'],
  ['6. Sustentación oral', '2', '15 y material de sustentación'],
], [62, 12, 26]));
add(p('La matriz completa de la rúbrica, con la evidencia, los archivos y las brechas de cada criterio, se entrega en `Matriz_Rubrica.md` y se resume en el Anexo A.', { after: 0 }));

// ---------------- 1. Continuidad
add(h1('1. Continuidad del proyecto: APF1 → APF2 → APF3'));
add(p('Cada avance construye sobre el anterior sin cambiar el problema, el alcance ni la alternativa elegida. El APF3 no repite el análisis del negocio: muestra cómo el diseño se convirtió en código y con qué evidencias se puede comprobar.'));
add(tabla(['Avance', 'Foco', 'Resultado principal'], [
  ['APF1', 'Contexto, problema, requisitos iniciales y alternativas', 'Caso académico de la barbería de Huamanga; selección preliminar de una web con backend Java'],
  ['APF2 (01/10/2026)', 'Canvas, tres alternativas con ocho pantallas cada una, procesos, clases, entidad relación, prototipo HTML y tres clases Java con Javadoc', 'Alternativa A1 elegida con 4,80 puntos; matriz RF-01…RF-14 → P01…P08; plan de pruebas CP-01…CP-12'],
  ['APF3 (este avance)', 'Construcción: arquitectura implementada, patrones, bibliotecas Java, Git/GitHub, interfaces funcionales, pruebas y despliegue', 'Sistema funcionando y desplegado; evidencias reproducibles por commit'],
], [16, 44, 40]));
add(h2('1.1 Correspondencia de identificadores'));
add(p('Los identificadores del APF2 se conservan. Cuando evolucionaron, la tabla indica su equivalente actual.'));
add(tabla(['APF2', 'Implementación actual', 'Observación'], [
  ['P01 Acceso y perfil', '`/ingresar`, `/registro`, `/privacidad`, `/perfil` (Mi cuenta), `/cambiar-password`', 'El registro exige teléfono y consentimiento de privacidad (RNF-12)'],
  ['P02 Reserva guiada', '`/reservar`', 'Asistente en tres pasos; el ADMIN reserva en nombre de un cliente (RF-18)'],
  ['P03 Mis citas e historial', '`/mis-citas` y diálogo de avisos', 'Reprogramar y cancelar según los permisos calculados por el servidor'],
  ['P04 Agenda y atención', '`/agenda` (BARBERO y ADMIN)', 'Vista diaria y semanal, transiciones de estado y auditoría (RF-17)'],
  ['P05 Servicios', '`/admin/servicios`', 'Alta, edición y desactivación'],
  ['P06 Barberos', '`/admin/barberos` y `/admin/usuarios`', 'Cuenta de personal con contraseña temporal; restablecer acceso (RF-19)'],
  ['P07 Horarios y bloqueos', '`/admin/horarios`', 'Jornada por intervalos; bloqueo para todo el equipo (RF-20)'],
  ['P08 Reportes', '`/admin/reportes`', 'Conteos por estado, servicio y profesional; historial filtrado; exportación a Excel (T-58)'],
  ['Clases del Anexo B (`ReservaService`, `IntervaloReserva`, `PoliticaReserva`)', '`ReglasTemporales`, `PoliticaTransiciones` y `ReservaService` del paquete `reservations`', 'Las reglas temporales del anexo pasaron al dominio y se prueban con los mismos siete casos'],
  ['CP-01…CP-12', 'CP-01…CP-20', 'CP-13…CP-19 se añadieron con las mejoras; CP-20 con T-58'],
], [24, 40, 36]));

// ---------------- 2. Problema, objetivos y alcance
add(h1Continuo('2. Problema, objetivos y alcance comprometido'));
add(p('La barbería coordina las citas por mensajes, llamadas y anotaciones dispersas, sin una disponibilidad única. Eso provoca citas superpuestas, demoras para confirmar y cambios sin trazabilidad. BarberTurno centraliza servicios, profesionales, horarios y reservas en un sistema web con reglas aplicadas en el servidor.'));
add(p('**Objetivo general.** Implementar un sistema web responsivo que centralice las reservas y turnos de una barbería de Huamanga, permita consultar disponibilidad, gestionar cambios y controlar la agenda diaria con trazabilidad.'));
add(tabla(['Incluido (implementado)', 'Fuera del alcance'], [
  ['Registro, acceso por roles, perfil y contraseña', 'Aplicación móvil nativa'],
  ['Catálogo, profesionales, jornadas, descansos y bloqueos', 'Pagos en línea, caja, inventario y facturación'],
  ['Disponibilidad, reserva, reprogramación y cancelación', 'Varias sedes'],
  ['Agenda, estados, auditoría, avisos internos, historial y reportes', 'WhatsApp, SMS y marketing automatizado'],
], [50, 50]));
add(p('**Propuestas futuras, no implementadas.** La multitenencia (varias barberías), nuevos roles de propietario o de plataforma, la reserva por WhatsApp y el uso de IA para atender clientes son ideas de evolución. No forman parte del APF3 ni se presentan como funciones existentes.'));

// ---------------- 3. Arquitectura
add(h1('3. Arquitectura implementada'));
add(p('BarberTurno es un **monolito modular**: un único proceso Java empaqueta la API y los archivos de la SPA. Con un solo desarrollador, una sede y decenas de reservas al día, un único proceso y una sola base de datos permiten transacciones ACID que cubren la reserva, la auditoría y el aviso en una misma operación, que es lo que exige la integridad de las reservas (RNF-05).'));
add(figura(dg('arquitectura'), 'Arquitectura implementada y despliegue. Elaboración propia a partir del código.', 16.5));
add(tabla(['Capa', 'Tecnología', 'Responsabilidad', 'Dónde está'], [
  ['Presentación', 'Angular 22, Angular Material, Tailwind 4', 'Pantallas por rol, formularios, navegación; sin reglas de negocio', '`frontend/src/app/features`'],
  ['Seguridad', 'Spring Security 7.1 (resource server JWT)', 'Autenticación, CSRF, rol por ruta, cabeceras', '`common/security`'],
  ['Controladores', 'Spring Web MVC (`@RestController`)', 'Recibir la petición, validar el DTO, delegar y responder JSON', '`*/…Controller.java`'],
  ['Servicios de dominio', 'Spring (`@Service`, `@Transactional`)', 'Reglas de negocio, autorización por propiedad, transacciones', '`*/…Service.java`, `reservations/*`'],
  ['Acceso a datos', 'Spring Data JPA, Hibernate 7.4', 'Consultas, bloqueos y persistencia', '`*/…Repository.java`'],
  ['Base de datos', 'PostgreSQL 18 + Flyway', 'Esquema, restricciones de exclusión, índices', '`db/migration/V1__esquema_inicial.sql`'],
], [16, 26, 34, 24]));
add(h2('3.1 Recorrido de una petición'));
add(p('Cuando un cliente confirma una reserva ocurre lo siguiente (el diagrama de secuencia de la sección 6 lo muestra en detalle):'));
add(numerada([
  'Angular envía `POST /api/reservas` con el cuerpo JSON, la cookie de sesión `BT_SESION` y la cabecera `X-XSRF-TOKEN`.',
  'La cadena de filtros de Spring Security valida el JWT de la cookie, compara el token CSRF de la cabecera con el de la cookie y comprueba que el rol sea CLIENTE o ADMIN. Si falla, responde 401 o 403 sin llegar al controlador.',
  '`ReservaController` recibe un `CrearReservaDto` validado con Bean Validation y llama a `ReservaService.crear`.',
  '`ReservaService` abre una transacción, bloquea las filas del cliente y del barbero, vuelve a calcular la disponibilidad y guarda la reserva, la auditoría y los avisos. Ante cualquier conflicto revierte todo.',
  'El controlador devuelve `201` con un `ReservaDto` que incluye los **permisos** de la reserva; la interfaz habilita los botones según esos permisos, sin reimplementar la política.',
]));

// ---------------- 4. Diseño: patrones y principios
add(h1('4. Diseño de la solución: MVC, DAO, SOLID y TDD'));
add(p('La rúbrica pide acreditar al menos tres elementos entre MVC, TDD, DAO y SOLID. Conviene distinguir su naturaleza: **MVC** es un patrón arquitectónico de presentación, **DAO** es un patrón de acceso a datos, **SOLID** es un conjunto de principios de diseño de clases y **TDD** es una práctica de desarrollo. Esta sección muestra dónde se aplica cada uno con código del repositorio.'));
add(tabla(['Elemento', 'Tipo', 'Aplicación en BarberTurno', 'Grado de evidencia'], [
  ['MVC', 'Patrón arquitectónico', 'Controladores REST + servicios/entidades (modelo) + vista Angular que consume JSON', 'Completo (todo el backend)'],
  ['DAO', 'Patrón de acceso a datos', '9 interfaces de repositorio Spring Data JPA (una por agregado); ninguna consulta SQL en servicios ni controladores', 'Completo'],
  ['SOLID', 'Principios de diseño', 'SRP, DIP e ISP con clases concretas', 'Ejemplos concretos'],
  ['TDD', 'Práctica de desarrollo', `Ciclos rojo → verde documentados en T-49 y en T-58; el resto del proyecto usó pruebas automatizadas, no TDD estricto`, 'Parcial y declarado'],
], [12, 20, 46, 22]));
add(h2('4.1 MVC en una arquitectura SPA + API'));
add(p('BarberTurno no usa vistas generadas en el servidor (no hay Thymeleaf). MVC se aplica en el backend con **Spring Web MVC**: el `DispatcherServlet` dirige cada petición a un **controlador**; el **modelo** son los servicios de dominio, las entidades y los DTO; y la **vista** es la representación JSON que produce el controlador y que Angular presenta en pantalla. Los controladores no contienen reglas: validan la entrada, obtienen el usuario autenticado y delegan.'));
add(codigo(`@RestController
public class ReporteController {
    private final ReporteService reportes;

    public ReporteController(ReporteService reportes) { this.reportes = reportes; }

    @GetMapping("/api/reportes/resumen")
    public ResumenReporteDto resumen(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long servicioId, @RequestParam(required = false) Long barberoId,
            @RequestParam Map<String, String> parametros) {
        // ... rechaza parámetros vacíos con 400 VALIDACION
        return reportes.resumen(actor, desde, hasta, servicioId, barberoId);   // delega en el modelo
    }
}`, 'backend/src/main/java/pe/barberturno/reporting/ReporteController.java (d2f3107, Javadoc omitido)'));
add(p('En el frontend, Angular mantiene la misma separación: los componentes de `features/` son la vista; los servicios de `core/api` encapsulan las llamadas HTTP; y las interfaces de `core/modelos` replican los DTO. Ocultar un botón no autoriza nada: la decisión siempre la toma el servidor.'));
add(h2('4.2 DAO con Spring Data JPA'));
add(p('Cada agregado tiene una interfaz de repositorio que oculta cómo se accede a PostgreSQL. Los servicios solo conocen métodos con significado de negocio (`buscarSolapamientos`, `bloquearPorIds`, `contarFuturasQueOcupan`); Spring Data genera la implementación en tiempo de ejecución. Así, las reglas se pueden probar sin escribir SQL en los servicios, y las consultas críticas quedan en un solo lugar.'));
add(codigo(`public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)                 // ③ bloqueo de la fila de la reserva
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> bloquearPorId(@Param("id") Long id);

    @Query("""
            select r from Reserva r
            where r.barbero.id = :barberoId
              and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA
              and r.inicio < :fin and r.fin > :inicio and r.id <> :excluirId
            order by r.inicio, r.id
            """)                                          // intervalos semiabiertos [inicio, fin)
    List<Reserva> buscarSolapamientosExcluyendo(@Param("barberoId") Long barberoId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin, @Param("excluirId") Long excluirId);
}

public interface BarberoRepository extends JpaRepository<Barbero, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)                 // ② serializa las escrituras por barbero
    @Query("select b from Barbero b where b.id in :ids order by b.id")
    List<Barbero> bloquearPorIds(@Param("ids") Collection<Long> ids);
}`, 'reservations/ReservaRepository.java y scheduling/BarberoRepository.java (extracto)'));
add(figura(gh('gh10-reservarepository'), 'ReservaRepository en GitHub, commit d2f3107. Captura propia del repositorio público (21/10/2026).', 15, 12));
add(h2('4.3 Principios SOLID con clases concretas'));
add(h3('Responsabilidad única (SRP)'));
add(p('Las reglas que cambian por motivos distintos están en clases distintas. `PoliticaTransiciones` solo decide si un cambio de estado es válido para un rol y un momento; no accede a la base de datos ni a la sesión. `ReservaAutorizacion` solo decide si un actor puede ver una reserva. `AuditoriaService` solo registra cambios. `ReservaService` coordina a todas ellas dentro de una transacción.'));
add(codigo(`public final class PoliticaTransiciones {
    public Resultado evaluar(Rol rol, boolean esBarberoAsignado, EstadoReserva actual, EstadoReserva destino,
                             Instant ahora, Instant inicio, Duration tolerancia, Duration anticipacionCliente, String motivo) {
        if (!actual.puedePasarA(destino)) return Resultado.TRANSICION_INVALIDA;          // RN-10
        if (destino == EstadoReserva.CANCELADA) {
            if (rol == Rol.BARBERO) return Resultado.PROHIBIDO;
            return new ReglasTemporales().puedeModificar(ahora, inicio, rol == Rol.ADMIN, motivo, anticipacionCliente)
                    ? Resultado.PERMITIDA : Resultado.FUERA_DE_VENTANA;                 // RN-07 y RN-08
        }
        if (rol != Rol.ADMIN && !(rol == Rol.BARBERO && esBarberoAsignado)) return Resultado.PROHIBIDO;  // RN-11
        if (destino == EstadoReserva.EN_ATENCION && ahora.isBefore(inicio.minus(tolerancia))) return Resultado.FUERA_DE_VENTANA;
        if (destino == EstadoReserva.NO_ASISTIO && ahora.isBefore(inicio)) return Resultado.FUERA_DE_VENTANA;  // RN-12
        return Resultado.PERMITIDA;
    }
}`, 'reservations/PoliticaTransiciones.java (validaciones de nulos omitidas)'));
add(p('**Problema que resuelve:** la misma política se usa para ejecutar la transición y para calcular `ReservaDto.permisos`, de modo que la interfaz y el servidor no pueden discrepar. Al ser una función pura, se prueba con todas las combinaciones sin levantar Spring.'));
add(h3('Inversión de dependencias (DIP)'));
add(p('Las clases de alto nivel dependen de abstracciones que se inyectan por constructor, nunca de implementaciones creadas con `new`. `ReservaService` recibe repositorios (interfaces), un `Clock` y un `PasswordEncoder`/`UsuarioActual` según el caso. El caso más útil es el reloj: el código de negocio nunca llama a `Instant.now()`; usa el `Clock` inyectado, que en producción es el reloj real de Lima y en las pruebas y la demostración es un reloj fijo (28/09/2026 09:00).'));
add(codigo(`@Bean
public Clock clock(Environment entorno) {
    String fijo = entorno.getProperty("barberturno.reloj-fijo");
    if (fijo != null && entorno.acceptsProfiles(Profiles.of("prod"))) {
        throw new IllegalStateException("barberturno.reloj-fijo no está permitido en prod.");
    }
    if (fijo != null && !fijo.isBlank() && entorno.acceptsProfiles(Profiles.of("demo", "test"))) {
        return Clock.fixed(OffsetDateTime.parse(fijo).toInstant(), TiempoNegocio.ZONA);
    }
    return Clock.system(TiempoNegocio.ZONA);
}

public ReservaService(ReservaRepository reservas, UsuarioRepository usuarios, BarberoRepository barberos,
        ServicioRepository servicios, DisponibilidadService disponibilidad, AuditoriaService auditoria,
        NotificacionService notificaciones, Clock clock, ParametrosReserva parametros, ReservaAutorizacion autorizacion)`,
'common/time/ClockConfig.java y constructor de reservations/ReservaService.java'));
add(h3('Segregación de interfaces (ISP)'));
add(p('Los servicios que solo necesitan saber quién es el usuario actual dependen de `UsuarioActual`, una interfaz de tres métodos, y no de todo el `SecurityContextHolder` de Spring. La implementación `UsuarioActualSecurity` traduce el contexto de seguridad; en una prueba basta con una implementación mínima.'));
add(codigo(`public interface UsuarioActual {
    long id();
    Rol rol();
    Optional<Long> barberoId();
}`, 'common/security/UsuarioActual.java'));
add(h2('4.4 TDD: qué se hizo y qué no'));
add(p('Tener muchas pruebas no demuestra TDD. TDD exige escribir primero una prueba que falla, después el código mínimo que la hace pasar y, finalmente, refactorizar. En BarberTurno la mayoría de las tareas se entregaron con la implementación y sus pruebas en el mismo ciclo (pruebas automatizadas, no TDD estricto). Hay dos evidencias verificables del ciclo rojo → verde:'));
add(tabla(['Tarea', 'Prueba en rojo', 'Corrección en verde', 'Evidencia'], [
  ['T-49 (corrección del 403 de Firefox, CSRF)', '`e9ddeed` y `21f86ad`: pruebas que reproducen el defecto; la segunda «fallaba 3/4 antes» contra Tomcat real', '`c669276` y `b60cbdc`', '`docs/pruebas/t-49.md` y `docs/tareas.md`'],
  ['T-58 (exportación a Excel)', T58.tddRojo, T58.tddVerde, '`docs/pruebas/t-58.md` (rama de T-58)'],
], [22, 34, 22, 22]));
add(figura(gh('gh14-commit-t49-rojo'), 'T-49 en GitHub, paso rojo: commit 21f86ad añade la prueba AuthCsrfHttpIT que reproduce el defecto. Captura propia.', 15.5, 7));
add(figura(gh('gh15-commit-t49-verde'), 'T-49 en GitHub, paso verde: commit b60cbdc corrige el defecto y la prueba pasa. Captura propia.', 15.5, 7));

// ---------------- 5. Seguridad
add(h1('5. Seguridad aplicada'));
add(p('La seguridad se diseñó en el servidor: la interfaz puede ocultar opciones, pero cada operación se autoriza en Java. La tabla resume cada control con su implementación y la prueba que lo comprueba.'));
add(tabla(['Control', 'Implementación', 'Evidencia'], [
  ['Contraseñas', 'BCrypt con coste 12 (`JwtConfig.passwordEncoder`); 8–72 caracteres y máximo 72 bytes UTF-8 (RN-25)', '`PoliticaPasswordTest`, `AuthIT`'],
  ['Sesión', 'JWT HS256 de 8 h en la cookie `BT_SESION` HttpOnly, `SameSite=Strict` y `Secure` en producción; API `STATELESS`; `token_version` revoca sesiones al cambiar la contraseña', '`AuthIT`, CP-17'],
  ['CSRF', 'Cookie `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN` en toda escritura, también con JWT (`DEFAULT_CSRF_MATCHER`); token estable durante la sesión y renovado al iniciar o cerrar sesión (DA-26, T-49)', 'Pruebas «sesión válida sin CSRF → 403» y «CSRF incorrecto → 403» en cada escritura'],
  ['Fuerza bruta', 'Tras 5 intentos fallidos la cuenta se bloquea 15 min; mensaje genérico; comparación con un hash ficticio cuando el correo no existe', 'CP-14'],
  ['Autorización por rol', 'Reglas por ruta y método en `SecurityFilterChain` y `denyAll()` para lo no listado', 'Matriz de permisos (arquitectura §7.2)'],
  ['Autorización por propiedad', '`ReservaAutorizacion`: una reserva ajena responde **404** sin revelar que existe', 'CP-02, `ReservaConsultaIT`'],
  ['Validación', 'Bean Validation en los DTO (`record`), restricciones `CHECK` en la base y errores RFC 9457 sin trazas', '`ManejadorErroresTest`, `ErroresMvcIT`'],
  ['Integridad', 'Transacciones, bloqueos ordenados y `EXCLUDE USING gist` (sección 6)', 'CP-03, CP-05, CP-13'],
  ['Cabeceras', 'HSTS, CSP restrictiva, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`', '`CabecerasSeguridadIT`; comprobado en Azure (§12)'],
  ['HTTPS', 'Lo termina Azure App Service (_HTTPS Only_, HTTP → 301)', 'Comprobado el 19/10/2026 y el 21/10/2026'],
  ['Secretos', 'Variables de entorno; `.local/` ignorado por Git; el perfil `prod` exige las variables y rechaza `demo` y el reloj fijo', '`.gitignore`, `application-prod.yml`'],
  ['Registros', 'Sin contraseñas, JWT, cookies ni datos personales completos; formato ECS en producción', 'Pruebas con `ListAppender` (§7.1)'],
], [16, 56, 28]));
add(codigo(`http.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .csrf(config -> config.spa().csrfTokenRepository(csrf)
        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
            @Override
            public <O extends CsrfFilter> O postProcess(O filtro) {
                // El resource server excluye bearer de CSRF por defecto.
                // Con JWT en cookie toda escritura sigue necesitando CSRF.
                filtro.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                return filtro;
            }
        }));`, 'common/security/SecurityConfig.java (extracto)'));
add(p('**Por qué esta corrección importa:** el soporte de JWT de Spring Security asume que el token viaja en una cabecera `Authorization`, que un sitio atacante no puede enviar, y por eso desactiva CSRF. En BarberTurno el JWT viaja en una cookie, que el navegador sí envía automáticamente; sin esta línea, un formulario de otro sitio podría ejecutar escrituras con la sesión del usuario. La regresión se detectó en la revisión de T-10/T-11 y desde entonces cada endpoint de escritura tiene pruebas sin CSRF y con CSRF incorrecto.'));
add(figura(gh('gh09-securityconfig'), 'Reglas de autorización por ruta y método en SecurityConfig (GitHub, d2f3107). Captura propia.', 15, 14));

// ---------------- 6. Persistencia e integridad
add(h1('6. Persistencia, integridad y prevención de solapamientos'));
add(p('El esquema se crea con Flyway (`V1__esquema_inicial.sql`) y se valida contra las entidades JPA al arrancar. Las fechas se guardan como instantes (`timestamptz`) y se presentan siempre en America/Lima. Los servicios y profesionales no se borran: se desactivan y conservan su historial.'));
add(figura(dg('modelo-datos'), 'Modelo de datos implementado (ocho tablas). Elaboración propia a partir de la migración V1.', 16.5));
add(h2('6.1 Por qué no basta con consultar y luego guardar'));
add(p('Dos clientes pueden consultar la misma franja libre antes de que alguno guarde. Si cada uno comprueba y después inserta, ambos verían la franja libre y se crearían dos citas superpuestas. BarberTurno lo evita con tres capas complementarias:'));
add(numerada([
  '**Serialización por barbero (bloqueo pesimista).** Toda escritura que afecta la disponibilidad de un barbero bloquea primero su fila (`FOR NO KEY UPDATE`) y **después** vuelve a comprobar la disponibilidad dentro de la misma transacción. La segunda solicitud espera y, cuando continúa, ve la reserva confirmada por la primera.',
  '**Red de seguridad en la base de datos.** Dos restricciones `EXCLUDE USING gist` impiden que un barbero o un cliente tengan dos reservas activas que se solapen, aunque algún camino de código omitiera el protocolo. El error `23P01` se traduce a un 409 comprensible.',
  '**Bloqueo optimista.** El campo `version` (`@Version`) detecta acciones sobre un estado desactualizado, por ejemplo un doble clic o dos pestañas: responde 409 `VERSION_DESACTUALIZADA`.',
]));
add(p('Para evitar interbloqueos, todas las operaciones toman los bloqueos en el mismo orden: ① fila del cliente → ② filas de barberos por id ascendente → ③ fila de la reserva.'));
add(codigo(`CONSTRAINT reserva_sin_solape_barbero EXCLUDE USING gist
  (barbero_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (estado <> 'CANCELADA'),
CONSTRAINT reserva_sin_solape_cliente EXCLUDE USING gist
  (cliente_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (estado <> 'CANCELADA')`, 'db/migration/V1__esquema_inicial.sql'));
add(figura(gh('gh11-migracion-exclude'), 'Restricciones de exclusión en la migración V1 publicada en GitHub (d2f3107). Captura propia.', 15.5, 9));
add(codigo(`var cliente = usuarios.bloquearPorId(asistida ? cmd.clienteId() : actor.id()).orElseThrow(this::noEncontrado); // ①
var perfiles = barberos.bloquearPorIds(List.of(cmd.barberoId()));                                               // ②
// ... servicio y barbero activos
Instant fin = new ReglasTemporales().calcularFin(inicio, servicio.getDuracionMin());
disponibilidad.validarFranja(barbero.getId(), inicio, fin, 0L).ifPresent(codigo -> {
    throw new NegocioException(codigo, "La franja solicitada no cumple la disponibilidad.");               // → ROLLBACK
});
if (!reservas.buscarSolapamientosCliente(cliente.getId(), inicio, fin, 0L).isEmpty()) { /* 409 RN-04 */ }
var reserva = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, inicio, estado, creador, ahora));
auditoria.registrarCambio(reserva, creador, AccionAuditoria.CREAR, /* ... */);                                  // misma transacción`,
'reservations/ReservaService.java, método crear (extracto)'));
add(figura(dg('secuencia-reserva'), 'Secuencia de creación de una reserva con el control de concurrencia. Elaboración propia a partir del código.', 16, 20));
add(p('**Evidencia:** `ConcurrenciaReservaIT` envía 10 solicitudes simultáneas a la misma franja contra PostgreSQL real y comprueba que exactamente una recibe 201 y nueve reciben 409; también cruza una reserva con un bloqueo simultáneo y una reprogramación con una creación (CP-03, CP-05). `EsquemaIT` comprueba directamente las restricciones `EXCLUDE` y que dos citas contiguas (10:00–10:30 y 10:30–11:00) sí se aceptan (CP-04).'));

// ---------------- 7. Bibliotecas Java
add(h1('7. Recursos Java: Logback, Apache POI, Apache Commons y Google Guava'));
add(p('La rúbrica pide incorporar Google Guava, Apache POI, Apache Commons y Logback, y el docente aclaró que no basta con declararlas: hay que mostrar dónde se aplican y qué aportan. La auditoría del 21/10/2026 sobre `pom.xml` y el árbol de dependencias efectivo encontró lo siguiente **antes** de este avance:'));
add(tabla(['Biblioteca', 'Situación en d2f3107', 'Uso en el código'], [
  ['Logback 1.5.38', 'Transitiva (spring-boot-starter-logging)', 'Sí: backend de los registros SLF4J; formato ECS en producción; pruebas con `ListAppender`'],
  ['Apache Commons', '`commons-lang3` 3.20.0 y `commons-logging` 1.3.6, solo transitivas', 'No'],
  ['Google Guava', 'Ausente', 'No'],
  ['Apache POI', 'Ausente', 'No'],
], [20, 40, 40]));
add(p('Una dependencia transitiva no acredita un uso funcional. Por eso se propuso al responsable, y este autorizó como excepción puntual al freeze, la tarea **T-58**: exportar a Excel el reporte que el administrador ya consulta (RF-14). Concentra las tres bibliotecas que faltaban en una función útil y acotada, sin alterar reglas de negocio ni dispersar dependencias por el dominio (MJ-22, DA-29).'));
add(notaBloque(T58.notaVersion));
add(h2('7.1 Logback'));
add(p('Spring Boot usa **SLF4J** como fachada (las clases escriben `LoggerFactory.getLogger(...)`) y **Logback** como implementación que formatea y escribe los eventos. Logback llega con `spring-boot-starter-logging` (1.5.38, versión gestionada por Boot 4.1.1); no hay `logback-spring.xml` porque la configuración se hace con propiedades de Spring Boot.'));
add(tabla(['Aspecto', 'Detalle'], [
  ['Artefacto', '`ch.qos.logback:logback-classic:1.5.38` (transitiva)'],
  ['Configuración', '`application.yml` (niveles), `application-dev.yml` (`pe.barberturno: DEBUG`), `application-prod.yml` (`logging.structured.format.console: ecs`)'],
  ['Uso', '`AuthService` (intentos fallidos y bloqueos por id), `ManejadorErrores` (rechazos y errores no controlados), `JwtConfig`, `DatosDemoRunner`' + (T58.listo ? ', `ReporteExcelService` (exportaciones)' : '')],
  ['Seguridad', 'Solo se registran identificadores numéricos y códigos; nunca contraseñas, JWT, cookies, correos ni nombres. Las pruebas lo verifican capturando los eventos de Logback'],
], [18, 82]));
add(codigo(`logging:
  structured:
    format:
      console: ecs            # application-prod.yml: JSON estructurado (Elastic Common Schema)

// AuthService.login
LOG.info("Login fallido de usuario id={}.", usuario.getId());       // solo el id, nunca el correo
if (bloquear) LOG.warn("Bloqueo temporal de usuario id={}.", usuario.getId());`, 'application-prod.yml y auth/AuthService.java'));
add(codigo(`Logger logger = (Logger) LoggerFactory.getLogger(ManejadorErrores.class);   // ch.qos.logback.classic.Logger
ListAppender<ILoggingEvent> captura = new ListAppender<>();
captura.start();
logger.addAppender(captura);
// ... se provoca un error 4xx con datos ficticios en la petición
var evento = captura.list.getFirst();
assertThat(evento.getLevel()).isEqualTo(Level.WARN);
assertThat(evento.getThrowableProxy()).isNull();                         // sin traza
assertThat(evento.getFormattedMessage()).contains("HTTP " + estado)
        .doesNotContain("secreto-ficticio", "persona@ejemplo.test", "Exception");`,
'test/.../common/error/ManejadorErroresTest.java: la prueba usa la API de Logback para comprobar que el registro no filtra datos'));
for (const sec of T58.secciones) {
  add(h2(sec.titulo));
  sec.parrafos.forEach((t) => add(p(t)));
  if (sec.tabla) add(tabla(['Aspecto', 'Detalle'], sec.tabla, [18, 82]));
  (sec.codigos || []).forEach((k) => add(codigo(k.texto, k.titulo)));
  (sec.despues || []).forEach((t) => add(p(t)));
}
add(h2('7.5 Resumen de dependencias y seguridad en el tratamiento de la información'));
add(tabla(['Biblioteca', 'Artefacto y versión', 'Tipo', 'Clase y método', 'Aporte', 'Seguridad'], T58.resumenBibliotecas, [13, 20, 11, 22, 17, 17], { size: 16 }));
add(figura(gh('gh18-reporteexcelservice'), 'ReporteExcelService publicado en GitHub (3bcd5d0): Guava Preconditions y Stopwatch, POI XSSFWorkbook y quotePrefixed, y el registro con Logback. Captura propia (21/10/2026).', 15.5, 15));

// ---------------- 8. Interfaces
add(h1('8. Interfaces gráficas implementadas'));
add(p('Todas las pantallas comprometidas en el APF2 están implementadas en Angular y funcionan contra la API real. La identidad visual «verde» (T-55 y T-57) se aplicó a todas ellas sin cambiar reglas, permisos ni contratos. Las capturas proceden del arnés de Playwright con datos ficticios y una base de pruebas aislada; cada una se tomó con análisis de accesibilidad axe sin violaciones y sin desplazamiento horizontal.'));
add(notaBloque(T58.notaCapturas));
add(tabla(['Rol', 'Pantallas y acciones disponibles'], [
  ['Público', 'Ingresar, crear cuenta (con consentimiento de privacidad), consultar servicios y disponibilidad'],
  ['Cliente', 'Reservar en tres pasos; Mis citas con filtros; reprogramar y cancelar dentro de la política de 2 h; avisos internos; Mi cuenta y contraseña'],
  ['Barbero', 'Agenda diaria y semanal propia; confirmar, iniciar, completar o marcar «no asistió»; ver la auditoría de sus reservas; avisos'],
  ['Administrador', 'Agenda de todos; reservar en nombre de un cliente; servicios; barberos; horarios y bloqueos (también para todo el equipo); reportes con exportación a Excel; usuarios y restablecimiento de acceso; cambios excepcionales con motivo'],
], [16, 84]));
add(h2('8.1 Cliente'));
add(figuraFila([cap('Ingresar360'), cap('Registro360'), cap('MiCuenta360')], 'Acceso en el teléfono (360 px): ingresar, crear cuenta con consentimiento y Mi cuenta.', 10));
add(figura(cap('Reservar1440Paso1'), 'Reserva guiada, paso 1 (1440 px): servicio, profesional, fecha y solo las franjas libres que calcula el servidor.', 16, 10.5));
add(figura(cap('Reservar1440Paso3'), 'Reserva guiada, paso 3: revisión con precio y duración antes de confirmar.', 16, 10.5));
add(figuraFila([cap('Reservar360Paso1'), cap('MisCitas360'), cap('Avisos360')], 'En el teléfono (360 px): reservar, Mis citas y avisos internos.', 10));
add(figura(cap('MisCitas1440'), 'Mis citas (1440 px): próximas e historial; los botones dependen de los permisos que envía el servidor.', 16, 10.5));
add(figura(cap('ReprogramarCliente1440'), 'Reprogramación por el cliente, permitida solo con dos horas o más de anticipación (RN-07).', 16, 10.5));
add(h2('8.2 Barbero'));
add(figura(cap('AgendaBarbero1440'), 'Agenda diaria del barbero (1440 px): solo sus citas, con las acciones de atención permitidas.', 16, 10.5));
add(figura(cap('AgendaSemanaBarbero1440'), 'Agenda semanal de lunes a domingo (1440 px).', 16, 12));
add(figuraFila([cap('Agenda360'), cap('MasAcciones360'), cap('AvisosBarbero360')], 'Agenda en el teléfono, acciones de atención y avisos del barbero (360 px).', 10));
add(h2('8.3 Administrador'));
add(figura(cap('Servicios1440'), 'Servicios (1440 px): alta, edición y desactivación; los inactivos no se ofrecen al reservar.', 16, 10.5));
add(figura(cap('Barberos1440'), 'Barberos: cuenta de personal con contraseña temporal y estado activo o inactivo (máximo diez activos).', 16, 10.5));
add(figura(cap('HorariosCarlos1440'), 'Horarios y bloqueos de un barbero: jornada semanal por intervalos y bloqueos excepcionales.', 16, 22));
add(figura(cap('Reportes1440'), 'Reportes (1440 px): conteos por estado, servicio y profesional, historial operativo con los mismos filtros y botón «Descargar Excel» (T-58).', 16, 16));
if (T58.capturaExcel) T58.capturaExcel.forEach((f) => add(figura(f.archivo, f.pie, 16, f.alto || 11)));
add(figura(cap('Usuarios1440'), 'Usuarios: búsqueda de clientes y personal y restablecimiento del acceso con contraseña temporal (RF-19).', 16, 15));
add(figura(cap('Asistida1440'), 'Reserva asistida: el administrador reserva en nombre de un cliente registrado (RF-18).', 16, 10.5));
add(figuraFila([cap('MenuAdmin360'), cap('Barberos360'), cap('DialogoServicio360')], 'Administración en 360 px: menú, barberos en tarjetas y diálogo de servicio.', 10));
add(h2('8.4 Estados de error, carga y acceso restringido'));
add(figuraFila([cap('Restringido360'), cap('ReportesError360'), cap('ReportesCarga360')], 'Acceso restringido por rol, error homogéneo y estado de carga (360 px).', 10));

// ---------------- 9. Matriz
add(h1('9. Matriz requisito → pantalla → implementación → prueba'));
add(p('La matriz conecta cada requisito con su pantalla, el código que lo implementa y las pruebas que lo verifican. Las pruebas `*IT` usan PostgreSQL real; los casos E2E recorren la interfaz en Chromium y Firefox a 360 y 1440 px.'));
add(tabla(['Req.', 'Pantalla', 'Implementación (backend)', 'Pruebas'], [
  ['RF-01 Registro', '/registro', '`AuthController`, `AuthService.registrar`', 'CP-01 · AuthIT · E2E 07'],
  ['RF-02 Autenticación', '/ingresar', '`AuthService.login`, `SecurityConfig`', 'CP-02, CP-14, CP-17 · AuthIT · E2E 05'],
  ['RF-03 Perfil', '/perfil', '`PerfilController`, `PerfilService`', 'CP-01 · PerfilIT · E2E 07'],
  ['RF-04 Servicios', '/admin/servicios', '`ServicioController`, `ServicioService`', 'CP-08 · ServicioIT · E2E 04'],
  ['RF-05 Barberos', '/admin/barberos', '`BarberoController`, `BarberoService`', 'CP-08 · BarberoIT'],
  ['RF-06 Horarios', '/admin/horarios', '`JornadaService`, `BloqueoService`', 'CP-07, CP-16 · JornadaIT, BloqueoIT · E2E 04'],
  ['RF-07 Disponibilidad', '/reservar', '`DisponibilidadService`, `CalculadoraFranjas`', 'CP-04 · DisponibilidadIT'],
  ['RF-08 Reserva', '/reservar', '`ReservaService.crear`', 'CP-03, CP-04, CP-13, CP-15 · ReservaCrearIT, ConcurrenciaReservaIT · E2E 01/06'],
  ['RF-09 Reprogramación', '/mis-citas, /agenda', '`ReservaService.reprogramar`', 'CP-05 · ReprogramacionIT · E2E 01'],
  ['RF-10 Cancelación', '/mis-citas, /agenda', '`ReservaService.cancelar`', 'CP-06 · CancelacionIT · E2E 01/02'],
  ['RF-11 Agenda', '/agenda', '`ReservaConsultaService`', 'CP-02 · ReservaConsultaIT · E2E 03'],
  ['RF-12 Atención', '/agenda', '`ReservaService.transicionar`, `PoliticaTransiciones`', 'CP-09 · TransicionesIT · E2E 03'],
  ['RF-13 Historial', '/mis-citas, /admin/reportes', '`ReservaConsultaService`, `ReservaFiltros`', 'CP-02, CP-10 · ReservaConsultaIT, ReporteIT'],
  ['RF-14 Reportes', '/admin/reportes', '`ReporteService`, `ReporteRepository`' + (T58.listo ? '; `ReporteExcelService`' : ''), 'CP-10' + (T58.listo ? ', CP-20' : '') + ' · ReporteIT' + (T58.listo ? ', ' + T58.pruebasRf14 : '') + ' · E2E 04'],
  ['RF-15 Contraseña', '/cambiar-password', '`AuthService`, `PasswordTemporalFilter`', 'CP-17 · PerfilIT'],
  ['RF-16 Avisos', 'campana, /mis-citas', '`NotificacionService`', 'CP-18 · AuditoriaAvisosIT · E2E 01'],
  ['RF-17 Auditoría', '/agenda', '`AuditoriaService`', 'CP-09 · AuditoriaAvisosIT'],
  ['RF-18 Reserva asistida', '/reservar (ADMIN)', '`ReservaService.crear` con `clienteId`', 'CP-19 · ReservaAsistidaIT'],
  ['RF-19 Restablecer acceso', '/admin/usuarios', '`UsuarioAdminService`', 'CP-17 · UsuarioAdminIT'],
  ['RF-20 Bloqueo del equipo', '/admin/horarios', '`BloqueoService` (lote)', 'CP-07 · BloqueoIT'],
  ['RF-21 Sin preferencia', '/reservar', '`DisponibilidadService`', 'DisponibilidadIT'],
], [17, 17, 33, 33], { size: 16 }));
add(p('Fuente: `docs/requisitos.md` §9 y `docs/pruebas/aceptacion.md` (matriz completa con los métodos de prueba). Los RNF se tratan en la sección 13.'));

// ---------------- 10. Git y GitHub
add(h1('10. Control de versiones con Git y GitHub'));
add(p(`El proyecto se versiona con Git desde su primer commit (01/10/2026) y se publica en el repositorio público [github.com/sohnny-lima/BarberTurno](${REPO}). A la fecha del informe, \`main\` en GitHub tiene **${T58.commitsMain} commits** y ${T58.mergesMain} merges de tareas, y cada push ejecuta la integración continua.`));
add(tabla(['Práctica', 'Cómo se aplicó', 'Evidencia'], [
  ['Una rama por tarea', '`tarea/T-XX-descripcion` desde `main`; integración con `git merge --no-ff` para conservar la historia de cada tarea', `${T58.ramasTarea} ramas de tarea; merges en \`main\``],
  ['Commits convencionales en español', '`feat(reservas): …`, `fix(auth): …`, `test(e2e): …`, `docs: …`, con la referencia `[T-XX]`', `feat ${T58.tipos.feat}, fix ${T58.tipos.fix}, test ${T58.tipos.test}, docs ${T58.tipos.docs}, chore/ci ${T58.tipos.chore}`],
  ['Integración continua', 'GitHub Actions `ci.yml`: backend `mvnw verify` con PostgreSQL 18 y frontend lint, formato, Vitest y build', 'Primer CI verde: ejecución 37314699514 (05/10/2026); CI de `d2f3107`: 39099141093 (success)'],
  ['Trazabilidad', 'Cada tarea registra fecha, hashes y notas de cierre en `docs/tareas.md`; cada requisito apunta a sus tareas y pruebas', '`docs/tareas.md`, `docs/requisitos.md` §9'],
  ['Protección de secretos', '`.gitignore` excluye `.local/`, `.env*`, claves, `target/`, `node_modules/`', 'Revisado antes de cada publicación'],
], [20, 50, 30]));
add(figura(gh('gh01-repositorio'), `Repositorio público en GitHub con ${T58.commitsMain} commits y CI correcta en el último commit. Captura propia (21/10/2026).`, 15.5, 11));
add(figura(gh('gh02-commits-main'), 'Historial de commits de main en GitHub (últimos avances del cierre y de la fase 2). Captura propia (21/10/2026).', 15.5, 13));
add(figura(gh('gh05-actions'), 'GitHub Actions: ejecuciones del flujo CI sobre main. Captura propia (21/10/2026).', 15.5, 10));
add(figura(gh('gh06-ci-d2f3107'), 'Ejecución 39099141093 sobre d2f3107: jobs backend y frontend correctos. Captura propia.', 15.5, 7));
add(figura(gh('gh16-ci-t58'), 'Ejecución 39716850704 tras integrar T-58 (12a4185, código de 3bcd5d0): jobs backend y frontend correctos. Captura propia (21/10/2026).', 15.5, 7));
add(figura(gh('gh17-merge-t58'), 'Merge sin avance rápido de T-58 en main (3bcd5d0), con sus dos padres. Captura propia (21/10/2026).', 15, 10));
add(h2('10.1 Evolución por fases'));
add(tabla(['Periodo', 'Tareas', 'Avance'], [
  ['01/10/2026', 'T-01…T-09', 'Repositorio, esqueletos, CI, medición de Java, esquema Flyway, entidades y reglas de dominio'],
  ['02–04/10/2026', 'T-10…T-37', 'Identidad, catálogo, horarios, disponibilidad, reservas con concurrencia, agenda, reportes, producción, E2E, carga y recuperación'],
  ['04–06/10/2026', 'T-38…T-49', 'Ajustes de revisión, Javadoc completo, primer CI remoto (T-47), corrección CSRF de Firefox (T-49)'],
  ['05–09/10/2026', 'T-48…T-56', 'Interfaz con Tailwind y dirección visual «verde» (fase 1)'],
  ['17–19/10/2026', 'T-57, P-03', 'Fase 2 visual, checkpoint, despliegue en Azure'],
  ['21/10/2026', 'T-58', 'Exportación a Excel con POI, Commons Lang, Guava y Logback, con TDD; integrada en `main` (merge `3bcd5d0`) y publicada'],
], [18, 16, 66]));
add(h2('10.2 Qué está publicado y qué no'));
add(notaBloque(T58.notaGit));

// ---------------- 11. Pruebas
add(h1('11. Pruebas y resultados técnicos'));
add(p('Los resultados se identifican con su commit, fecha y entorno. Las cifras del checkpoint no se presentan como ejecuciones nuevas.'));
add(tabla(['Nivel', 'Herramienta', 'Resultado', 'Procedencia'], [
  ['Backend (unitarias + integración con PostgreSQL)', 'JUnit 6, AssertJ, MockMvc', '**1548/1548**', 'Checkpoint del coordinador 17–18/10/2026, `mvnw clean verify`, código = `d2f3107`'],
  ['Frontend', 'Vitest', '**442/442** en Lima y en Madrid', 'Mismo checkpoint'],
  ['E2E', 'Playwright + axe', '**48/48** (Chromium y Firefox, 360 y 1440 px)', 'Mismo checkpoint'],
  ['Build de producción', 'Angular CLI', 'Inicial **496,39 kB** (presupuesto 500 kB, sin elevar)', 'Mismo checkpoint'],
  ['CI remota', 'GitHub Actions', '**success** (39099141093) y **success** (39716850704)', '`d2f3107`, 18/10/2026; `12a4185` (código de `3bcd5d0`, T-58), 21/10/2026'],
  ['Cobertura de líneas', 'JaCoCo (umbral ≥ 70 % en `reservations` y `scheduling`, obligatorio en `verify`)', 'Último registro publicado: backend 98,94 %, reservations 100 %, scheduling 99,50 %', '`docs/pruebas/aceptacion.md` (T-37, 05/10/2026); el umbral se vuelve a exigir en cada `verify`'],
  ['Carga (RNF-01)', 'Gatling', '50 usuarios concurrentes; p95 ≤ 2 s en disponibilidad', 'T-35/T-43, equipo local (no producción)'],
  ['Recuperación (RNF-09)', '`tools/respaldo.sh`, `restaurar.sh`', 'Ensayo local de respaldo y restauración verificado', 'T-36 (local; en Azure, pendiente)'],
  ...T58.filasPruebas,
], [22, 22, 28, 28], { size: 16 }));
add(h2('11.1 Participación de Java frente al compromiso del APF2'));
add(p('El APF2 estimó para A1 una participación de Java del **60 %** y definió cómo verificarla (sección 6 del APF2): líneas de código fuente propias en Java divididas entre todas las líneas de código fuente del producto, sin dependencias, archivos generados, comentarios, líneas vacías ni documentación, informando el resultado con y sin pruebas. El repositorio aplica exactamente ese método con `node tools/medir-java.mjs`, que cuenta `.java`, `.ts`, `.html`, `.scss`, `.css` y `.sql` en `backend/src`, `frontend/src`, `frontend/e2e` y `perf`, y deja cada medición fechada en `docs/pruebas/medicion-java.md`.'));
add(tabla(['Fecha (Lima)', 'Commit', 'Momento del proyecto', 'Java sin pruebas', 'Java con pruebas'], [
  ['01/10/2026', '`919e9ca`', 'Esqueletos del backend y del frontend', '51,47 %', '72,15 %'],
  ['04/10/2026', '`8b22a16`', 'Dominio, API y seguridad avanzados; poca interfaz', '61,76 %', '75,75 %'],
  ['04/10/2026', '`5ac8d01`', 'Interfaces de reserva, agenda y reportes', '42,79 %', '58,79 %'],
  ['04/10/2026', '`94dbe99`', 'Cierre de T-01…T-47', '40,25 %', '55,02 %'],
  ['21/10/2026', '`12a4185`', 'Tras las fases visuales (T-48, T-55, T-57) y T-58; versión entregada', '**28,78 %**', '**41,56 %**'],
], [14, 12, 42, 16, 16]));
add(tabla(['Líneas de producto', 'Java', 'TypeScript', 'HTML', 'SCSS/CSS', 'SQL', 'Total'], [
  ['05/10/2026 (`94dbe99`)', '4284', '3978', '1704', '568', '109', '10 643'],
  ['21/10/2026 (`12a4185`)', '4444', '5032', '2675', '3180', '109', '15 440'],
  ['Variación', '+160', '+1054', '+971', '+2612', '0', '+4797'],
], [24, 11, 13, 11, 13, 9, 19]));
add(p('**Qué muestra la medición.** La brecha frente al 60 % es real: con el método comprometido, Java representa el 28,78 % del código de producto y el 41,56 % contando las pruebas. La caída no se debe a que la lógica haya salido de Java: entre el 05/10 y el 21/10 el código Java creció (+160 líneas, sobre todo T-58), mientras que la capa de presentación creció en 4637 líneas, en su mayor parte hojas de estilo (+2612 líneas de SCSS/CSS) y plantillas de las fases visuales aprobadas. Las reglas de negocio, la autorización, las transacciones, la persistencia y los reportes siguen íntegramente en Java; el frontend no contiene reglas (sección 4).'));
add(p('**Qué implica.** (1) El 60 % del APF2 era una estimación hecha antes de diseñar la interfaz definitiva y no se cumple; se informa tal cual. (2) El responsable reclasificó el porcentaje como métrica informativa el 04/10/2026 (RA-02, P-02 en `docs/requisitos.md`); el requisito que sí se mantiene es que el backend y toda la lógica estén en Java, y se cumple. Esa reclasificación es una decisión del proyecto: si el docente evalúa el porcentaje, la brecha debe defenderse con estos datos. (3) No se cambió la métrica ni se añadió código para inflarla: hacerlo iría contra la claridad del diseño (DA-15). (4) GitHub muestra Java 50,1 %, pero mide bytes por archivo con otro criterio (Linguist); no sustituye al método comprometido y no se usa como evidencia.'));

// ---------------- 12. Despliegue
add(h1Continuo('12. Despliegue en Azure y monitorización'));
add(p('El responsable creó los recursos y desplegó manualmente el 19/10/2026 en **Azure for Students**, región West US. La aplicación se publica como un único jar construido con el perfil `con-frontend`, de modo que la SPA y la API comparten origen, sin CORS.'));
add(tabla(['Recurso', 'Configuración'], [
  ['App Service `barberturno-web-202610`', 'Linux B1, Java SE 21, _Always On_, _HTTPS Only_, _Health check_ en `/actuator/health`'],
  ['PostgreSQL `pg-barberturno-202610`', 'Flexible Server 18.6 B1ms, 32 GB, copias automáticas con retención de 7 días; base `barberturno_prod` con rol de aplicación propio y `btree_gist`; conexión TLS `verify-full`'],
  ['Artefacto', 'Jar con SHA-256 `aa4dcf8f…88bc2`, código idéntico a `d2f3107`; despliegue `b850dc93…` con resultado `RuntimeSuccessful`'],
  ['Monitor externo', 'UptimeRobot, `GET /actuator/health` cada 5 minutos con alertas por correo'],
  ['Coste', '≈ 13,14 USD/mes del crédito de 100 USD (App Service); PostgreSQL B1ms dentro del beneficio gratuito, pendiente de confirmar en _Usage_'],
], [28, 72]));
add(figuraFila([az('az01-ingresar-1440'), az('az02-ingresar-360')], 'Producción en Azure: página de ingreso en 1440 y 360 px. Capturas propias por GET público, sin iniciar sesión (21/10/2026).', 6.5));
add(figura(az('az03-health'), 'GET /actuator/health en producción: 200 {"status":"UP"} (21/10/2026).', 8, 3));
add(p('**Comprobaciones con peticiones públicas (19/10 y 21/10/2026):** salud 200 UP; HTTP → 301 a HTTPS; HSTS, CSP, `X-Frame-Options: DENY`, `nosniff` y `Referrer-Policy`; cookie `XSRF-TOKEN` con `Secure; SameSite=Strict`; Swagger no público. El responsable inició sesión como administrador y entró en Agenda.'));
add(notaBloque(T58.notaDespliegue));

// ---------------- 13. Aceptación, limitaciones y pendientes
add(h1('13. Estado de aceptación, limitaciones y pendientes'));
add(p('La cobertura funcional implementada no equivale a la aceptación completa. La tabla separa lo acreditado de lo que todavía requiere evidencia.'));
add(tabla(['Elemento', 'Estado', 'Detalle'], [
  ['RF-01…RF-21', 'Implementados y probados', 'Pruebas de integración y E2E en entorno local; CI en GitHub'],
  ['CP-01…CP-19 (salvo CP-11)', 'Acreditados (local)', '`docs/pruebas/aceptacion.md`'],
  ['CP-20 (exportación)', T58.listo ? 'Acreditado (T-58, en `main`)' : 'Pendiente de T-58', '`docs/pruebas/t-58.md`'],
  ['CP-11 · usabilidad con cinco participantes (RNF-06)', '**Pendiente: 0/5 sesiones**', 'Material preparado sobre la versión final; no se sustituye por E2E ni por el ensayo piloto'],
  ['RNF-02 · 99 % de disponibilidad mensual', '**En observación, no cumplido**', 'Monitor desde el 19/10/2026; periodo propuesto: noviembre de 2026 completo'],
  ['Prueba mínima en producción (PM-1…PM-8)', '**Pendiente**', 'Solo PM-0 (HTTPS y cabeceras) hecho; las escrituras requieren autorización del responsable'],
  ['Restauración en Azure (RNF-09)', '**Preparada, no ejecutada**', 'Ensayo local hecho (T-36); en Azure, procedimiento y coste estimados'],
  ['RNF-07 · navegadores comerciales', 'Parcial', 'Chromium y Firefox de Playwright; falta la matriz de versiones comerciales'],
  ['RNF-03 · HTTPS en producción', 'Cumplido', 'Azure _HTTPS Only_ y HSTS'],
  ['Validación con el negocio', 'Pendiente', 'Caso académico: reglas y precios propuestos, sin representante de una barbería real'],
], [30, 22, 48]));
add(h2('13.1 Limitaciones conocidas'));
add(vinetas([
  'La reserva por autoservicio queda confirmada de inmediato; el flujo con confirmación manual existe como parámetro, pero el negocio no lo ha validado.',
  'No hay notificaciones por correo, SMS ni WhatsApp: los avisos son internos (alcance del MVP).',
  'La regla de red de la base depende de las IP salientes de App Service; si Azure las cambia, el monitor lo detectaría y habría que actualizar el firewall.',
  '`HEAD /` responde sin cabeceras de seguridad (GET es correcto); no afecta a los navegadores.',
  'La CSP admite `style-src \'unsafe-inline\'` por compatibilidad con Angular Material; un nonce sería una mejora futura.',
]));

// ---------------- 14. Autoría
add(h1('14. Autoría, uso de asistentes de IA y dominio del código'));
add(p('El proyecto se desarrolló con asistentes de IA bajo la dirección del estudiante, y así se declara. La rúbrica valora que el código sea del estudiante **o que este lo domine**; por eso esta sección explica cómo se repartió el trabajo y cómo se prepara la defensa.'));
add(tabla(['Participante', 'Papel'], [
  ['Sohnny Walter Lima Infanzón (responsable)', 'Define el problema y el alcance, toma las decisiones (alternativa, freeze, despliegue, excepciones como T-58), aprueba las integraciones, despliega en Azure, ejecutará CP-11 y sustenta.'],
  ['Claude Code (Anthropic)', 'Arquitectura, requisitos, plan de tareas, revisión técnica de cada entrega y redacción de documentación; implementaciones pequeñas de coordinación. 103 commits de `main` llevan su coautoría explícita.'],
  ['Codex (OpenAI)', 'Implementación principal del código y de las pruebas, mediante encargos con criterios de aceptación; sus commits figuran con la identidad Git del responsable.'],
  ['Gemini (Google)', 'Revisión independiente, en solo lectura, de las tareas críticas.'],
], [30, 70]));
add(p('GitHub muestra a «sohnny-lima» y «claude» como colaboradores porque Git registra los autores y la coautoría. Para demostrar dominio, el guion de sustentación incluye el recorrido de una petición, las preguntas de defensa con respuestas basadas en el código real y los archivos que conviene abrir durante la exposición.'));

// ---------------- 15. Sustentación
add(h1('15. Propuesta de sustentación'));
add(p('El aula no fija una estructura ni una duración para la exposición; la siguiente es una **propuesta** de 10 a 12 minutos con una demostración de unos 4 minutos. El detalle, las frases clave y las preguntas de defensa están en `Guion_Sustentacion.md` y en la presentación `APF3_BarberTurno_Sustentacion.pptx`.'));
add(tabla(['Bloque', 'Minutos', 'Contenido'], [
  ['1. Contexto y continuidad', '1', 'Problema, alcance y paso del prototipo del APF2 al sistema'],
  ['2. Arquitectura y recorrido de una petición', '2', 'Angular → Spring Security → controlador → servicio → repositorio → PostgreSQL'],
  ['3. Patrones y seguridad', '2', 'MVC, DAO, SOLID, TDD (lo que sí y lo que no) y controles de seguridad'],
  ['4. Bibliotecas Java', '1,5', 'Logback, POI, Commons Lang y Guava en el código de la exportación'],
  ['5. Demostración', '4', 'Cliente reserva; barbero atiende; administrador gestiona, consulta reportes y exporta a Excel'],
  ['6. Git, pruebas y despliegue', '1,5', 'Historial y CI en GitHub; resultados; Azure'],
  ['7. Pendientes y cierre', '0,5', 'CP-11, RNF-02 y prueba en producción; conclusiones'],
], [38, 12, 50]));

// ---------------- 16. Conclusiones
add(h1Continuo('16. Conclusiones'));
add(vinetas([
  'El diseño del APF2 se implementó sin cambiar el alcance: los catorce requisitos comprometidos funcionan para los tres roles y se añadieron siete mejoras trazables.',
  'La arquitectura aplica MVC en el backend Spring, DAO con repositorios Spring Data y principios SOLID verificables; TDD se aplicó y documentó en tareas concretas, sin atribuirlo al proyecto entero.',
  'La integridad de las reservas no depende de la interfaz: bloqueos ordenados, revalidación en la transacción y restricciones de exclusión en PostgreSQL lo garantizan, y las pruebas de concurrencia lo comprueban.',
  'Las cuatro bibliotecas de la rúbrica tienen un uso real y explicado; las tres incorporadas en T-58 se limitan a una función útil y protegen la información exportada.',
  'El historial de Git, la CI y las evidencias por commit permiten reproducir los resultados; el despliegue en Azure funciona con HTTPS y monitorización.',
  'Quedan pendientes la usabilidad con personas, la disponibilidad mensual y la prueba funcional en producción; se informan como tales.',
]));

// ---------------- Referencias
add(h1('Referencias'));
[
  'Lima Infanzón, S. W. (2026). _BarberTurno: Avance de Proyecto Final 2_ [Informe académico]. Universidad Tecnológica del Perú.',
  'Universidad Tecnológica del Perú. (2026). _Rúbrica SI53-APF3-2026A: Avance de Proyecto Final 3_. Curso Integrador I: Sistemas Software.',
  'Spring. (2026). _Spring Boot Reference Documentation 4.1_. https://docs.spring.io/spring-boot/',
  'Spring. (2026). _Spring Security Reference: Cross Site Request Forgery_. https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html',
  'PostgreSQL Global Development Group. (2026). _Range Types: Constraints on Ranges_. https://www.postgresql.org/docs/18/rangetypes.html',
  'The Apache Software Foundation. (2025). _Apache POI: the Java API for Microsoft Documents_. https://poi.apache.org/',
  'The Apache Software Foundation. (2025). _Apache Commons Lang_. https://commons.apache.org/proper/commons-lang/',
  'Google. (2026). _Guava: Google Core Libraries for Java_. https://github.com/google/guava',
  'QOS.ch. (2026). _Logback Project_. https://logback.qos.ch/',
  'OWASP Foundation. (s. f.). _CSV Injection_. https://owasp.org/www-community/attacks/CSV_Injection',
  'Fowler, M. (2002). _Patterns of Enterprise Application Architecture_. Addison-Wesley.',
  'Martin, R. C. (2017). _Clean Architecture_. Prentice Hall.',
  'Beck, K. (2002). _Test-Driven Development: By Example_. Addison-Wesley.',
  `Lima Infanzón, S. W. (2026). _Repositorio BarberTurno_ [Código fuente]. GitHub. ${REPO}`,
].forEach((r) => add(p(r, { align: AlignmentType.LEFT, indent: { left: 360, hanging: 360 } })));

// ---------------- Anexos
add(h1('Anexo A. Resumen de la matriz de la rúbrica'));
add(p('Autoevaluación preliminar del estudiante con la evidencia disponible; no sustituye la evaluación del docente. El detalle está en `Matriz_Rubrica.md`.'));
add(tabla(['Criterio', 'Máx.', 'Evidencia principal', 'Brecha pendiente'], T58.matrizResumen, [20, 7, 43, 30], { size: 16 }));
add(h1Continuo('Anexo B. Inventario de evidencias y reproducción'));
add(tabla(['Evidencia', 'Ubicación'], [
  ['Requisitos, arquitectura, tareas y decisiones', '`docs/requisitos.md`, `docs/arquitectura.md`, `docs/tareas.md`'],
  ['Aceptación CP-01…CP-19 y RNF', '`docs/pruebas/aceptacion.md`'],
  ['Capturas de la interfaz (auditadas con axe)', 'En la entrega, `evidencias/interfaces/` (89 capturas sobre `e0ebc54`, 21/10/2026, con su índice); históricas en el repositorio: `docs/pruebas/t-55/capturas/` y `docs/pruebas/t-57/capturas/`'],
  ['Exportación a Excel (T-58)', '`docs/pruebas/t-58.md` en la rama `tarea/T-58-exportar-excel`; en la entrega, `evidencias/t-58/` con `reporte-ejemplo.xlsx` (datos ficticios) y su vista en Excel'],
  ['Despliegue', '`docs/p-03-alojamiento.md` §12; `evidencias/azure/`'],
  ['GitHub', `${REPO} y \`evidencias/github/\``],
  ['Diagramas editables', '`fuentes/diagramas/*.svg`'],
], [40, 60]));
add(p('**Reproducir localmente** (JDK 21, Node 24.21.0 con fnm y PostgreSQL 18 en el puerto 5433): preparar la base con `tools/db-local.sql`; backend `cd backend && ./mvnw verify`; frontend `cd frontend && npm ci && npm run lint && npm test -- --watch=false && npm run build`; demostración con `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` y `BT_DEMO_PASSWORD`. Los detalles están en el `README.md` del repositorio y de la entrega.'));

// ---------------- Documento
const doc = new Document({
  creator: 'Sohnny Walter Lima Infanzón', title: 'BarberTurno · APF3', description: 'Avance de Proyecto Final 3',
  styles: {
    default: { document: { run: { font: FUENTE, size: 22 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 34, bold: true, color: VERDE, font: FUENTE }, paragraph: { spacing: { before: 120, after: 200 }, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 26, bold: true, color: '1E2B26', font: FUENTE }, paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 1 } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 23, bold: true, color: VERDE, font: FUENTE }, paragraph: { spacing: { before: 180, after: 80 }, outlineLevel: 2 } },
      { id: 'TOC1', name: 'toc 1', basedOn: 'Normal', next: 'Normal', run: { size: 20, bold: true }, paragraph: { spacing: { before: 60, after: 0 } } },
      { id: 'TOC2', name: 'toc 2', basedOn: 'Normal', next: 'Normal', run: { size: 19 }, paragraph: { spacing: { before: 0, after: 0 }, indent: { left: 300 } } },
    ],
  },
  numbering: { config: [
    { reference: 'vinetas', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } },
      { level: 1, format: LevelFormat.BULLET, text: '–', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 900, hanging: 270 } } } }] },
    { reference: 'numeros', levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 300 } } } }] },
  ] },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1300, bottom: 1200, left: 1440, right: 1440, header: 600, footer: 600 } }, titlePage: true },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [new TextRun({ text: 'CURSO INTEGRADOR I   |   BARBERTURNO   |   APF3', size: 16, color: GRIS })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [new TextRun({ text: 'BarberTurno · ', size: 16, color: GRIS }), new TextRun({ children: [PageNumber.CURRENT], size: 18 })] })] }) },
    children: c,
  }],
});
Packer.toBuffer(doc).then((b) => { fs.writeFileSync(process.argv[2], b); console.log('escrito', process.argv[2], b.length); });
