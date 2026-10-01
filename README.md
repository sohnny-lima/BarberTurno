# BarberTurno

Sistema web de reservas y turnos para una barbería de una sede en Huamanga, Perú. Proyecto académico de Integrador I: Sistemas Software (UTP). Arquitectura A1: Angular 22, Spring Boot 4.1.1 / Java 21 y PostgreSQL 18; toda la lógica de negocio reside en Java. Los datos de demostración son ficticios.

Esta entrega prepara el repositorio y la base de datos local (T-01). El backend se creará en T-02 y el frontend en T-03; sus comandos de arranque se documentan abajo para esas etapas.

## Entorno

| Herramienta | Requisito |
|---|---|
| Java | JDK 21; instalación de referencia: `C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot` |
| Backend | Spring Boot 4.1.1 y Maven Wrapper; no requiere instalar Maven |
| Frontend | Angular 22 y Node 24 LTS (≥ 24.15), con npm; instalar Node antes de T-03 |
| Base de datos | PostgreSQL 18 en `localhost:5433`; el PostgreSQL 17 de `:5432` no se usa |
| Control de versiones | Git, rama principal `main` |

No se requiere Docker para las pruebas locales. La zona horaria del negocio es `America/Lima`.

## Preparar PostgreSQL local

Desde la raíz del proyecto, con PostgreSQL 18 iniciado:

```powershell
psql -X -h localhost -p 5433 -U postgres -d postgres -f tools/db-local.sql
```

Si `psql` no está en el PATH, o apunta al cliente 17, use el ejecutable 18:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -h localhost -p 5433 -U postgres -d postgres -f tools/db-local.sql
```

Introduzca la contraseña de `postgres` cuando se solicite. Al crear el rol `barberturno` (o si ya existe sin contraseña), el script solicita dos veces una contraseña de desarrollo para ese rol mediante `\password`, sin mostrarla. Consérvela fuera del repositorio y úsela posteriormente como `BT_DB_PASSWORD`.

El script crea únicamente lo que falta: el rol de aplicación sin privilegios administrativos y las bases `barberturno` y `barberturno_test`, ambas propiedad de ese rol. Puede ejecutarlo dos veces para comprobar la idempotencia. Conserva los datos y las contraseñas existentes; si una base ya tiene otro dueño o el rol tiene permisos incompatibles, se detiene para su revisión. No crea tablas: las migraciones Flyway corresponden a T-06.

Para automatizar la autenticación administrativa, puede usar un archivo local `.local/pgpass.conf` (ignorado por Git) y definir `$env:PGPASSFILE` con su ruta absoluta. El formato de una entrada es `localhost:5433:postgres:postgres:<contraseña local>`. Restrinja el acceso al archivo a su usuario; no comparta ni versione su contenido.

## Configuración de la aplicación

Los secretos se suministrarán mediante variables de entorno o `backend/.env.local`, ignorado por Git. Ese archivo no se carga automáticamente: deberá exportar las variables a la sesión que ejecuta Maven.

| Variable | Uso |
|---|---|
| `BT_DB_URL` | Desarrollo: `jdbc:postgresql://localhost:5433/barberturno`; pruebas: `jdbc:postgresql://localhost:5433/barberturno_test` |
| `BT_DB_USER` | `barberturno` |
| `BT_DB_PASSWORD` | Contraseña local del rol de aplicación |
| `BT_JWT_SECRET` | Secreto de ≥ 32 bytes, codificado en Base64 |
| `BT_ADMIN_CORREO`, `BT_ADMIN_PASSWORD`, `BT_ADMIN_NOMBRE` | Administrador inicial |
| `BT_COOKIE_SECURE` | Según el perfil; desarrollo HTTP local sin `Secure`, producción con `Secure` |

La configuración definitiva de perfiles y variables se implementará en T-02 y las tareas de seguridad correspondientes; véase [arquitectura §9](docs/arquitectura.md#9-configuración-y-entornos).

## Arranque y verificaciones a partir de T-02 y T-03

Backend, en una terminal PowerShell con las variables configuradas:

```powershell
Set-Location backend
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

La API usará `http://localhost:8080`; la comprobación de salud será `GET /actuator/health`. El perfil `dev,demo` estará disponible después de T-32. En Linux/macOS use `./mvnw`.

Frontend, en otra terminal, después de instalar Node 24 LTS:

```powershell
Set-Location frontend
npm ci
npm start
```

La SPA usará `http://localhost:4200`, con proxy `/api` hacia `:8080`. Calidad del frontend:

```powershell
npm run lint
npm test -- --watch=false
npm run build
```

E2E (`npx playwright test`) se incorporará en T-34 y la medición de Java (`node tools/medir-java.mjs`, desde la raíz) en T-05.

## Organización y trabajo

- [AGENTS.md](AGENTS.md): responsabilidades, autonomía y convenciones comunes.
- [CLAUDE.md](CLAUDE.md): instrucciones del arquitecto.
- [Requisitos](docs/requisitos.md): RF, RNF, reglas y trazabilidad.
- [Arquitectura](docs/arquitectura.md): módulos, DDL, API, seguridad y concurrencia.
- [Tareas](docs/tareas.md): estado, dependencias y notas de cierre.
- [Material APF2](docs/apf2/): referencia original congelada; sus bytes y finales de línea se conservan sin normalización.
- [Evidencias](docs/pruebas/): comprobaciones y resultados.
- `tools/db-local.sql`: preparación idempotente de PostgreSQL local.

T-01 establece el primer commit en `main`. Para las tareas posteriores se usan ramas `tarea/T-XX-descripcion-corta`, commits Conventional Commits en español con `[T-XX]` e integración local mediante `git merge --no-ff`. No se configura ni se publica un remoto en esta etapa.

Los archivos de trabajo usan UTF-8 y LF; `.cmd`, `.bat` y `.ps1` usan CRLF según `.gitattributes`. `docs/apf2/` está exceptuado de la normalización y del formato automático para preservar el original.
