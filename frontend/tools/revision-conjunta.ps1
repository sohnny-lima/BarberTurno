# Revisión conjunta (V2-07…V2-10) con backend real sobre la base desechable barberturno_v2_test.
# Uso (PowerShell 7, desde cualquier carpeta):
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1              # pide clave demo (oculta) y abre ventanas
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Verificar   # clave aleatoria; comprueba cuentas, avisos y sondas
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Recuperar   # recupera una sesión interrumpida y sale
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Mantener    # solo pruebas: mantiene el backend hasta matar la consola
#
# Garantías y límites del Job Object de Windows (JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE):
# - Cubre: si la consola muere de forma abrupta (cierre de ventana o Stop-Process -Force), el núcleo de Windows
#   cierra el identificador del trabajo y termina el backend (java) y el proceso de ventanas (node y navegadores hijos).
# - Límites: (1) el Job Object no ejecuta el bloque finally ni vacía la base ni borra el cerrojo o la sesión;
#   (2) existe una ventana de unos milisegundos entre Start-Process y AssignProcessToJobObject. Ambos límites los
#   cubre la recuperación automática al siguiente arranque o mediante -Recuperar.
param(
  [switch]$Verificar,
  [switch]$Recuperar,
  [switch]$Mantener,
  [string]$Entorno = 'D:\Proyectos\BarberTurno-v2\.local\barberturno.env',
  [string]$Cerrojo = 'D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock',
  [string[]]$Sondas = @('axe.mjs')
)
$ErrorActionPreference = 'Stop'
$herramientas = $PSScriptRoot
$frontend = Split-Path -Parent $herramientas
$raiz = Split-Path -Parent $frontend
$jar = Join-Path $raiz 'backend\target\barberturno-0.0.1-SNAPSHOT.jar'
$dirLocal = Join-Path $raiz '.local'
$archivoSesion = Join-Path $dirLocal 'revision-conjunta.sesion.json'
$registro = Join-Path $dirLocal 'revision-conjunta-backend.log'
$fnm = 'C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe\fnm.exe'
$baseDatos = 'barberturno_v2_test'
$puerto = 8081
$utf8SinBom = [Text.UTF8Encoding]::new($false)

$trabajo = $null
$backend = $null
$ventanas = $null
$cerrojoPropio = $false
$sesionPropia = $false
$basePreparada = $false
$horaInicio = (Get-Date).ToString('o')

if (-not ('BarberTurno.Revision.TrabajoRevision' -as [type])) {
  Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

namespace BarberTurno.Revision {
  [StructLayout(LayoutKind.Sequential)]
  public struct JOBOBJECT_BASIC_LIMIT_INFORMATION {
    public long PerProcessUserTimeLimit;
    public long PerJobUserTimeLimit;
    public uint LimitFlags;
    public UIntPtr MinimumWorkingSetSize;
    public UIntPtr MaximumWorkingSetSize;
    public uint ActiveProcessLimit;
    public UIntPtr Affinity;
    public uint PriorityClass;
    public uint SchedulingClass;
  }

  [StructLayout(LayoutKind.Sequential)]
  public struct IO_COUNTERS {
    public ulong ReadOperationCount;
    public ulong WriteOperationCount;
    public ulong OtherOperationCount;
    public ulong ReadTransferCount;
    public ulong WriteTransferCount;
    public ulong OtherTransferCount;
  }

  [StructLayout(LayoutKind.Sequential)]
  public struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION {
    public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
    public IO_COUNTERS IoInfo;
    public UIntPtr ProcessMemoryLimit;
    public UIntPtr JobMemoryLimit;
    public UIntPtr PeakProcessMemoryUsed;
    public UIntPtr PeakJobMemoryUsed;
  }

  public sealed class TrabajoRevision : IDisposable {
    private const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;
    private const int JobObjectExtendedLimitInformation = 9;
    public static TrabajoRevision Actual;
    private IntPtr _handle;

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateJobObjectW(IntPtr lpJobAttributes, string lpName);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetInformationJobObject(
      IntPtr hJob,
      int infoType,
      ref JOBOBJECT_EXTENDED_LIMIT_INFORMATION lpJobObjectInfo,
      uint cbJobObjectInfoLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool AssignProcessToJobObject(IntPtr hJob, IntPtr hProcess);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr hObject);

    public static TrabajoRevision Crear() {
      IntPtr h = CreateJobObjectW(IntPtr.Zero, null);
      if (h == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
      var info = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
      info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
      uint size = (uint)Marshal.SizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>();
      if (!SetInformationJobObject(h, JobObjectExtendedLimitInformation, ref info, size)) {
        int err = Marshal.GetLastWin32Error();
        CloseHandle(h);
        throw new Win32Exception(err);
      }
      var instancia = new TrabajoRevision { _handle = h };
      Actual = instancia;
      return instancia;
    }

    public void Asignar(IntPtr hProcess) {
      if (_handle == IntPtr.Zero || hProcess == IntPtr.Zero) return;
      if (!AssignProcessToJobObject(_handle, hProcess)) {
        throw new Win32Exception(Marshal.GetLastWin32Error());
      }
    }

    public void Dispose() {
      if (_handle != IntPtr.Zero) {
        CloseHandle(_handle);
        _handle = IntPtr.Zero;
      }
      if (ReferenceEquals(Actual, this)) Actual = null;
    }
  }
}
'@
}

function Proceso-Vivo([int]$idProceso) {
  if ($idProceso -le 0) { return $false }
  return $null -ne (Get-Process -Id $idProceso -ErrorAction SilentlyContinue)
}

function Obtener-PidCerrojo {
  if (!(Test-Path -LiteralPath $Cerrojo)) { return $null }
  $texto = [IO.File]::ReadAllText($Cerrojo)
  if ($texto -match '(?i)\bpid\s*=\s*(\d+)\b') { return [int]$matches[1] }
  if ($texto -match '^\s*(\d+)\s*$') { return [int]$matches[1] }
  return $null
}

function Verificar-CerrojoLibre {
  if (!(Test-Path -LiteralPath $Cerrojo)) { return }
  $pidCerrojo = Obtener-PidCerrojo
  if ($null -ne $pidCerrojo -and $pidCerrojo -ne $PID -and (Proceso-Vivo $pidCerrojo)) {
    throw "El cerrojo de la base ($Cerrojo) pertenece a otro proceso vivo (PID $pidCerrojo). Se aborta sin tocar nada."
  }
}

function Cargar-EntornoProceso {
  $rutaEnv = $Entorno
  if (!(Test-Path -LiteralPath $rutaEnv)) {
    $alternativa = Join-Path $dirLocal 'barberturno.env'
    if (Test-Path -LiteralPath $alternativa) { $rutaEnv = $alternativa }
    else { throw "No se encontró el archivo de entorno en $Entorno." }
  }
  foreach ($linea in [IO.File]::ReadAllLines($rutaEnv)) {
    if ($linea -match '^\s*(BT_[A-Z0-9_]+)\s*=(.*)$') {
      [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
  }
  $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
  $env:BT_E2E_DB = $baseDatos
  $env:BT_DB_URL = "jdbc:postgresql://localhost:5433/$baseDatos"
  $env:BT_DB_USER = 'barberturno_v2_app'
  $env:BT_FLYWAY_USER = 'barberturno_v2'
  $env:BT_COOKIE_SECURE = 'false'
  & $fnm env --use-on-cd --version-file-strategy recursive --shell powershell | Out-String | Invoke-Expression
  Push-Location $frontend; & $fnm use | Out-Null; Pop-Location
}

function Limpiar-EntornoProceso {
  foreach ($v in 'BT_DEMO_PASSWORD', 'BT_ADMIN_PASSWORD', 'BT_JWT_SECRET', 'BT_DB_PASSWORD', 'BT_FLYWAY_PASSWORD') {
    Remove-Item "Env:$v" -ErrorAction SilentlyContinue
  }
}

function Vaciar-Base {
  # Misma limpieza guardada que el E2E: solo barberturno_v2_test, sin otras sesiones abiertas.
  Push-Location $frontend
  try {
    $ultimoError = $null
    for ($intento = 1; $intento -le 15; $intento++) {
      & node --input-type=module -e "const m = await import('./e2e/base-datos.ts'); m.vaciar();"
      if ($LASTEXITCODE -eq 0) { return }
      $ultimoError = "No se pudo vaciar $baseDatos."
      Start-Sleep -Milliseconds 200
    }
    throw $ultimoError
  } finally { Pop-Location }
}

function Preparar-AvisosRevision {
  # Garantiza que en barberturno_v2_test existan avisos no leídos para los cuatro papeles de la revisión
  # manual (cliente, profesional, encargado y propietario), usando únicamente el rol barberturno_v2_app.
  Push-Location $frontend
  try {
    $codigo = @'
const { sql } = await import('./e2e/base-datos.ts');
const r = sql(`BEGIN;
SELECT set_config('app.negocio_id', (SELECT id::text FROM negocio WHERE slug = 'barberturno'), true);
INSERT INTO notificacion (negocio_id, usuario_id, reserva_id, tipo, mensaje, leida, creado_en)
SELECT app_negocio_actual(), u.id, res.id, d.tipo, d.mensaje, false, CURRENT_TIMESTAMP
FROM (VALUES
  ('multirol@ejemplo.test', 'WHATSAPP', 'No se pudo enviar por WhatsApp el mensaje de la reserva BT-1: destinatario no válido. Contacte al cliente por otro medio.'),
  ('encargado@ejemplo.test', 'WHATSAPP', 'No se pudo confirmar si se envió por WhatsApp el mensaje de la reserva BT-1. Compruébelo con el cliente.'),
  ('personal-cliente@ejemplo.test', 'CREAR', 'Reserva BT-1 asignada en BarberTurno para la revisión de avisos.')
) AS d(correo, tipo, mensaje)
JOIN usuario u ON u.correo = d.correo
CROSS JOIN (SELECT min(id) AS id FROM reserva) res
WHERE res.id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM notificacion n WHERE n.usuario_id = u.id AND n.negocio_id = app_negocio_actual()
  );
COMMIT;`);
if (r.status !== 0) process.exit(1);
'@
    & node --input-type=module -e $codigo
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar los avisos de demostración.' }
  } finally { Pop-Location }
}

function Escribir-Sesion($pidBackendActual, $pidVentanasActual) {
  New-Item -ItemType Directory -Force $dirLocal | Out-Null
  $datos = [ordered]@{
    pidConsola  = $PID
    pidBackend  = $pidBackendActual
    pidVentanas = $pidVentanasActual
    jar         = $jar
    base        = $baseDatos
    puerto      = $puerto
    hora        = $horaInicio
    inicio      = $horaInicio
    worktree    = $raiz
  }
  $json = $datos | ConvertTo-Json -Depth 4
  [IO.File]::WriteAllText($archivoSesion, $json, $utf8SinBom)
}

function Detener-ProcesoRegistrado($idProceso, [string]$patronLinea, [string]$etiqueta) {
  if ($null -eq $idProceso) { return "$etiqueta sin PID registrado" }
  $id = [int]$idProceso
  if (!(Proceso-Vivo $id)) { return "$etiqueta (PID $id) ya no existía" }
  $procCim = Get-CimInstance Win32_Process -Filter "ProcessId = $id" -ErrorAction SilentlyContinue
  if ($null -eq $procCim -or [string]::IsNullOrWhiteSpace($procCim.CommandLine)) {
    if (!(Proceso-Vivo $id)) { return "$etiqueta (PID $id) ya no existía" }
    return "$etiqueta (PID $id) omitido: sin línea de órdenes verificable"
  }
  if ($procCim.CommandLine -notmatch $patronLinea) {
    return "$etiqueta (PID $id) omitido: su línea de órdenes no coincide"
  }
  Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
  Wait-Process -Id $id -Timeout 10 -ErrorAction SilentlyContinue
  return "$etiqueta detenido (PID $id)"
}

function Recuperar-Sesion {
  Verificar-CerrojoLibre
  $sesion = $null
  if (Test-Path -LiteralPath $archivoSesion) {
    $sesion = [IO.File]::ReadAllText($archivoSesion, $utf8SinBom) | ConvertFrom-Json
    if ($null -ne $sesion.pidConsola -and ([int]$sesion.pidConsola -ne $PID) -and (Proceso-Vivo ([int]$sesion.pidConsola))) {
      throw "La consola registrada en la sesión (PID $($sesion.pidConsola)) sigue viva. Se aborta sin tocar nada."
    }
  } elseif (Test-Path -LiteralPath $Cerrojo) {
    $pidCerrojo = Obtener-PidCerrojo
    throw "Existe el cerrojo $Cerrojo (PID '$pidCerrojo') sin archivo de sesión propio. Se aborta sin tocar nada."
  }

  $resumen = [System.Collections.Generic.List[string]]::new()
  if ($null -ne $sesion) {
    $patronJar = if ($sesion.jar) { [Regex]::Escape([string]$sesion.jar) + '|barberturno-0\.0\.1-SNAPSHOT\.jar' } else { 'barberturno-0\.0\.1-SNAPSHOT\.jar' }
    $resumen.Add((Detener-ProcesoRegistrado $sesion.pidBackend $patronJar 'backend'))
    $resumen.Add((Detener-ProcesoRegistrado $sesion.pidVentanas 'ventanas\.mjs' 'ventanas'))
  }

  Vaciar-Base
  $resumen.Add("base $baseDatos vaciada")

  if (Test-Path -LiteralPath $Cerrojo) {
    $pidCerrojo = Obtener-PidCerrojo
    if ($null -ne $sesion -and $null -ne $sesion.pidConsola -and $null -ne $pidCerrojo -and ([int]$pidCerrojo -eq [int]$sesion.pidConsola) -and !(Proceso-Vivo ([int]$sesion.pidConsola))) {
      Remove-Item -LiteralPath $Cerrojo -Force
      $resumen.Add("cerrojo borrado (PID $pidCerrojo)")
    } else {
      $resumen.Add("cerrojo conservado (PID '$pidCerrojo' no coincide con la consola registrada)")
    }
  }

  if (Test-Path -LiteralPath $archivoSesion) {
    Remove-Item -LiteralPath $archivoSesion -Force
    $resumen.Add('archivo de sesión borrado')
  }

  Write-Output ('Recuperación completada: ' + ($resumen -join '; ') + '.')
}

try {
  # 1. Comprobar cerrojo ajeno antes de tocar cualquier recurso.
  Verificar-CerrojoLibre
  Cargar-EntornoProceso

  # 2. Recuperación explícita (-Recuperar) o automática si quedó un archivo de sesión previo.
  if ($Recuperar) {
    Recuperar-Sesion
    return
  }
  if (Test-Path -LiteralPath $archivoSesion) {
    Write-Output 'Se detectó un archivo de sesión previo; ejecutando recuperación antes de arrancar...'
    Recuperar-Sesion
  }
  Verificar-CerrojoLibre
  if (Test-Path -LiteralPath $Cerrojo) {
    $pidRestante = Obtener-PidCerrojo
    throw "El cerrojo $Cerrojo sigue presente (PID '$pidRestante') y no pertenece a una sesión recuperable. Se aborta sin tocar nada."
  }

  # 3. Comprobaciones previas de procesos, puerto y artefacto antes de tomar el cerrojo.
  if (Test-Path (Join-Path $frontend 'tmp\e2e.lock')) { throw 'Hay un E2E en curso (tmp\e2e.lock).' }
  if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) {
    throw "El puerto $puerto está ocupado: detenga ese proceso antes de la revisión."
  }
  $javas = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'barberturno-0\.0\.1-SNAPSHOT\.jar|spring-boot:run' }
  if ($javas) { throw ('Ya hay un backend de BarberTurno en marcha: PID ' + ($javas.ProcessId -join ', ')) }
  $mavens = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'maven|surefire|failsafe' }
  if ($mavens) { throw 'Hay pruebas de Maven en curso: no se comparte PostgreSQL entre suites.' }
  if (!(Test-Path -LiteralPath $jar)) { throw 'Falta el jar con el frontend: ejecute antes npm run e2e o mvnw -Pcon-frontend package.' }
  Write-Output ('Jar: ' + (Get-Item -LiteralPath $jar).LastWriteTime.ToString('dd/MM/yyyy HH:mm'))

  # 4. Credenciales ficticias efímeras en el proceso (nunca se muestran).
  $env:BT_JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
  if ($Verificar -or $Mantener) {
    $env:BT_DEMO_PASSWORD = 'Demo1' + [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(16))
  } else {
    while ($true) {
      $segura = Read-Host -AsSecureString 'Clave para las cuentas ficticias (8 a 72 caracteres, con letra y dígito; no se muestra)'
      $clave = [Net.NetworkCredential]::new('', $segura).Password
      if ($clave.Length -ge 8 -and [Text.Encoding]::UTF8.GetByteCount($clave) -le 72 -and $clave -match '\p{L}' -and $clave -match '\d') { break }
      Write-Output 'La clave no cumple la política (RN-25). Inténtelo de nuevo.'
    }
    $env:BT_DEMO_PASSWORD = $clave
    Remove-Variable clave
  }
  $alias = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(4)).ToLower()
  $env:BT_ADMIN_CORREO = "admin-$alias@ejemplo.test"
  $env:BT_ADMIN_NOMBRE = "Administrador de revisión $alias"
  $env:BT_ADMIN_PASSWORD = $env:BT_DEMO_PASSWORD

  # 5. Tomar el cerrojo de la base y escribir el archivo de sesión ANTES de arrancar nada.
  New-Item -ItemType Directory -Force (Split-Path -Parent $Cerrojo) | Out-Null
  $lineaCerrojo = "pid=$PID worktree=$raiz inicio=$horaInicio"
  $flujoCerrojo = [IO.File]::Open($Cerrojo, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
  try {
    $bytesCerrojo = $utf8SinBom.GetBytes($lineaCerrojo + "`n")
    $flujoCerrojo.Write($bytesCerrojo, 0, $bytesCerrojo.Length)
  } finally {
    $flujoCerrojo.Dispose()
  }
  $cerrojoPropio = $true

  Escribir-Sesion $null $null
  $sesionPropia = $true

  # 6. Crear Job Object, vaciar la base y arrancar el backend asignándolo de inmediato al trabajo.
  $trabajo = [BarberTurno.Revision.TrabajoRevision]::Crear()
  $basePreparada = $true
  Vaciar-Base

  $backend = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') `
    -ArgumentList @('-jar', $jar, '--spring.profiles.active=dev,demo', "--server.port=$puerto") `
    -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -RedirectStandardOutput $registro `
    -RedirectStandardError ($registro -replace '\.log$', '-error.log')
  $trabajo.Asignar($backend.Handle)
  Escribir-Sesion $backend.Id $null
  Write-Output ("Backend: PID $($backend.Id) en http://localhost:$puerto ($baseDatos)")

  $listo = $false
  for ($i = 0; $i -lt 180; $i++) {
    if ($backend.HasExited) { throw 'El backend terminó antes de estar listo (ver .local\revision-conjunta-backend*.log).' }
    try {
      $sur = Invoke-RestMethod -Uri "http://localhost:$puerto/api/n/sur/barberos" -TimeoutSec 2
      if (@($sur).Count -ge 1) { $listo = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 500
  }
  if (!$listo) { throw 'El backend no estuvo listo en 90 s.' }
  Preparar-AvisosRevision
  Write-Output 'Backend listo con los datos ficticios de las dos barberías y los avisos de revisión.'

  if ($Mantener) {
    # 7a. Modo de prueba de cierre abrupto: espera indefinidamente sin abrir ventanas.
    Write-Output "Sesión en espera (-Mantener): consola PID $PID, backend PID $($backend.Id)."
    while (!$backend.HasExited) {
      Start-Sleep -Seconds 1
    }
  } elseif ($Verificar) {
    # 7b. Comprobación automática de cuentas, papeles, avisos y sondas (sin mostrar la clave).
    foreach ($cuenta in 'multirol', 'personal-cliente', 'encargado', 'cliente', 'plataforma') {
      $web = New-Object Microsoft.PowerShell.Commands.WebRequestSession
      Invoke-WebRequest -Uri "http://localhost:$puerto/api/auth/sesion" -WebSession $web -SkipHttpErrorCheck | Out-Null
      $xsrf = [Uri]::UnescapeDataString(($web.Cookies.GetCookies("http://localhost:$puerto") | Where-Object Name -eq 'XSRF-TOKEN').Value)
      $cuerpo = @{ correo = "$cuenta@ejemplo.test"; password = $env:BT_DEMO_PASSWORD } | ConvertTo-Json
      $r = Invoke-WebRequest -Uri "http://localhost:$puerto/api/auth/login" -Method Post -Body $cuerpo `
        -ContentType 'application/json' -Headers @{ 'X-XSRF-TOKEN' = $xsrf } -WebSession $web -SkipHttpErrorCheck
      if ($r.StatusCode -ne 200) { throw "Ingreso de $cuenta rechazado: $($r.StatusCode)" }
      $negocios = Invoke-RestMethod -Uri "http://localhost:$puerto/api/mis-negocios" -WebSession $web
      $detalleAvisos = ''
      if ($cuenta -ne 'plataforma') {
        $conteo = Invoke-RestMethod -Uri "http://localhost:$puerto/api/n/barberturno/notificaciones/conteo" -WebSession $web
        if ($conteo.noLeidas -lt 1) { throw "La cuenta $cuenta no tiene avisos sin leer en barberturno." }
        $detalleAvisos = "; avisos sin leer en barberturno: $($conteo.noLeidas)"
        if ($cuenta -eq 'personal-cliente') {
          $conteoCliente = Invoke-RestMethod -Uri "http://localhost:$puerto/api/n/barberturno/notificaciones/conteo" `
            -Headers @{ 'X-BT-Papel' = 'CLIENTE' } -WebSession $web
          if ($conteoCliente.noLeidas -lt 1) { throw 'El papel CLIENTE de personal-cliente no tiene avisos sin leer.' }
        }
      }
      Write-Output ("$cuenta@ejemplo.test: ingreso 200; barberías: " + ((@($negocios) | ForEach-Object { $_.slug + ' (' + ($_.papeles -join '+') + ')' }) -join ', ') + $detalleAvisos)
    }
    Push-Location $frontend
    try {
      $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $frontend 'tmp\navegadores'
      foreach ($sonda in $Sondas) {
        $rutaSonda = if (Test-Path -LiteralPath $sonda) { $sonda } else { Join-Path $herramientas (Join-Path 'revision' $sonda) }
        & node $rutaSonda
        if ($LASTEXITCODE -ne 0) { throw "La sonda $sonda no terminó correctamente." }
      }
    } finally { Pop-Location }
  } else {
    # 7c. Ventanas interactivas asignadas al Job Object; la revisión termina al cerrarlas.
    $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $frontend 'tmp\navegadores'
    $scriptVentanas = Join-Path $herramientas 'revision\ventanas.mjs'
    $ventanas = Start-Process -FilePath 'node' -ArgumentList @($scriptVentanas) `
      -WorkingDirectory $frontend -NoNewWindow -PassThru
    $trabajo.Asignar($ventanas.Handle)
    Escribir-Sesion $backend.Id $ventanas.Id
    $ventanas.WaitForExit()
    if ($ventanas.ExitCode -ne 0) { throw "El proceso de ventanas terminó con código $($ventanas.ExitCode)." }
  }
} finally {
  try {
    if ($ventanas -and !$ventanas.HasExited) {
      Stop-Process -Id $ventanas.Id -Force -ErrorAction SilentlyContinue
      $ventanas.WaitForExit()
    }
    if ($backend -and !$backend.HasExited) {
      Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue
      $backend.WaitForExit()
      Write-Output ('Backend detenido: PID ' + $backend.Id)
    }
    if ($trabajo) {
      $trabajo.Dispose()
      $trabajo = $null
    }
    if ($basePreparada) {
      Vaciar-Base
      Write-Output "$baseDatos vaciada."
    }
  } finally {
    if ($sesionPropia -and (Test-Path -LiteralPath $archivoSesion)) {
      Remove-Item -LiteralPath $archivoSesion -Force -ErrorAction SilentlyContinue
    }
    if ($cerrojoPropio -and (Test-Path -LiteralPath $Cerrojo)) {
      Remove-Item -LiteralPath $Cerrojo -Force -ErrorAction SilentlyContinue
    }
    Limpiar-EntornoProceso
  }
}
