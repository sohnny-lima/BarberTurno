# Revisión conjunta (V2-07…V2-10) con backend real sobre la base desechable barberturno_v2_test.
# Uso (PowerShell 7, desde cualquier carpeta):
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1              # pide clave demo (oculta) y abre ventanas
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Verificar   # clave aleatoria; comprueba cuentas, avisos y sondas
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Recuperar   # recupera una sesión interrumpida y sale
#   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Mantener    # solo pruebas: mantiene el backend hasta matar la consola
#
# Garantías y límites del Job Object de Windows (JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE):
# - Cubre: el propio proceso de la consola (pwsh) se asigna al Job Object antes de crear ningún proceso
#   hijo. Así todo hijo (backend java, ventanas node, sondas de -Verificar y navegadores de Playwright)
#   nace ya dentro del trabajo, sin ventana entre creación y asignación. Si la consola termina de forma
#   abrupta (cierre de ventana o Stop-Process -Force) o normal, el núcleo de Windows cierra el último
#   identificador del trabajo y termina los procesos hijos asociados (en el cierre normal esto ocurre al
#   salir el proceso de la consola, cuando los hijos ya fueron detenidos en el bloque finally).
# - Límites: el Job Object solo termina procesos; no ejecuta el bloque finally, no vacía la base de datos
#   ni borra el cerrojo o el archivo de sesión. Si la consola muere de forma abrupta o si falla el vaciado
#   (limpiezaPendiente = true), la recuperación automática al siguiente arranque o mediante -Recuperar
#   vacía la base y limpia la sesión y el cerrojo verificando el identificador único de la sesión.
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
$jar = [IO.Path]::GetFullPath((Join-Path $raiz 'backend\target\barberturno-0.0.1-SNAPSHOT.jar'))
$dirLocal = [IO.Path]::GetFullPath((Join-Path $raiz '.local'))
$archivoSesion = Join-Path $dirLocal 'revision-conjunta.sesion.json'
$cerrojoRecuperacion = Join-Path $dirLocal 'revision-conjunta.recuperacion.lock'
$registro = Join-Path $dirLocal 'revision-conjunta-backend.log'
$fnm = 'C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe\fnm.exe'
$baseDatos = 'barberturno_v2_test'
$puerto = 8081
$utf8SinBom = [Text.UTF8Encoding]::new($false)

$trabajo = $null
$consolaEnTrabajo = $false
$backend = $null
$ventanas = $null
$sondaActiva = $null
$infoBackend = $null
$infoVentanas = $null
$infoSonda = $null
$cerrojoPropio = $false
$sesionPropia = $false
$basePreparada = $false
$idSesion = [Guid]::NewGuid().ToString('D')
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
    private static extern bool IsProcessInJob(IntPtr ProcessHandle, IntPtr JobHandle, out bool Result);

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

    public bool Pertenece(IntPtr hProcess) {
      if (_handle == IntPtr.Zero || hProcess == IntPtr.Zero) return false;
      if (!IsProcessInJob(hProcess, _handle, out bool pertenece)) {
        throw new Win32Exception(Marshal.GetLastWin32Error());
      }
      return pertenece;
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

function Obtener-PidArchivo([string]$rutaArchivo) {
  if (!(Test-Path -LiteralPath $rutaArchivo)) { return $null }
  try {
    $texto = [IO.File]::ReadAllText($rutaArchivo, $utf8SinBom)
    if ($texto -match '(?i)\bpid\s*=\s*(\d+)\b') { return [int]$matches[1] }
    if ($texto -match '^\s*(\d+)\s*$') { return [int]$matches[1] }
  } catch { }
  return $null
}

function Obtener-PidCerrojo {
  return Obtener-PidArchivo $Cerrojo
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
    for ($intento = 1; $intento -le 10; $intento++) {
      & node --input-type=module -e "const m = await import('./e2e/base-datos.ts'); m.vaciar();"
      if ($LASTEXITCODE -eq 0) { return }
      $ultimoError = "No se pudo vaciar $baseDatos (existen sesiones activas u otra condición rechazada por vaciar())."
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
  ('multirol@ejemplo.test', 'WHATSAPP', U&'No se pudo enviar por WhatsApp el mensaje de la reserva BT-1: destinatario no v\\00E1lido. Contacte al cliente por otro medio.'),
  ('encargado@ejemplo.test', 'WHATSAPP', U&'No se pudo confirmar si se envi\\00F3 por WhatsApp el mensaje de la reserva BT-1. Compru\\00E9belo con el cliente.'),
  ('personal-cliente@ejemplo.test', 'CREAR', U&'Reserva BT-1 asignada en BarberTurno para la revisi\\00F3n de avisos.')
) AS d(correo, tipo, mensaje)
JOIN usuario u ON u.correo = d.correo
CROSS JOIN (SELECT min(id) AS id FROM reserva) res
WHERE res.id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM notificacion n WHERE n.usuario_id = u.id AND n.negocio_id = app_negocio_actual()
  );
COMMIT;`);
if (r.status !== 0) {
  let err = (r.stderr || (r.error && r.error.message) || r.stdout || '').trim();
  err = err.replace(/(password|secret|token|clave|contrase\u00f1a|contrasena)\s*[:=]\s*\S+/gi, '$1=***');
  if (err) process.stderr.write(err);
  process.exit(r.status ?? 1);
}
'@
    $salidaNode = try {
      & node --input-type=module -e $codigo 2>&1
    } catch {
      $_
    }
    if ($LASTEXITCODE -ne 0) {
      $detalle = ($salidaNode | Out-String).Trim()
      $mensaje = if ($detalle) {
        "No se pudieron preparar los avisos de demostración: $detalle"
      } else {
        'No se pudieron preparar los avisos de demostración.'
      }
      throw $mensaje
    }
  } finally { Pop-Location }
}

function Construir-LineaOrdenes([string]$ejecutable, [string[]]$argumentos) {
  $exeCitado = if ($ejecutable.StartsWith('"') -and $ejecutable.EndsWith('"')) { $ejecutable } else { '"' + $ejecutable + '"' }
  if ($null -eq $argumentos -or $argumentos.Count -eq 0) { return $exeCitado }
  return ($exeCitado + ' ' + ($argumentos -join ' '))
}

function Normalizar-LineaOrdenes([string]$linea) {
  if ([string]::IsNullOrWhiteSpace($linea)) { return '' }
  $limpia = $linea.Trim()
  if ($limpia -match '^"([^"]+)"(\s+.*)?$') {
    return ($matches[1].Trim() + $matches[2])
  }
  return $limpia
}

function Construir-InfoProceso([System.Diagnostics.Process]$proceso, [string]$ejecutable, [string[]]$argumentos) {
  $ejecutableAbs = [IO.Path]::GetFullPath($ejecutable)
  return [ordered]@{
    pid          = [int]$proceso.Id
    creacion     = $proceso.StartTime.ToUniversalTime().ToString('o')
    ejecutable   = $ejecutableAbs
    lineaOrdenes = (Construir-LineaOrdenes $ejecutableAbs $argumentos)
  }
}

function Actualizar-InfoProcesoCim([int]$idProceso, $infoProceso) {
  if ($null -eq $infoProceso) { return }
  $procCim = Get-CimInstance Win32_Process -Filter "ProcessId = $idProceso" -ErrorAction SilentlyContinue
  if ($null -ne $procCim) {
    if (-not [string]::IsNullOrWhiteSpace($procCim.ExecutablePath)) {
      $infoProceso.ejecutable = [IO.Path]::GetFullPath($procCim.ExecutablePath)
    }
    if (-not [string]::IsNullOrWhiteSpace($procCim.CommandLine)) {
      $infoProceso.lineaOrdenes = $procCim.CommandLine.Trim()
    }
  }
}

function Escribir-Sesion([bool]$limpiezaPendiente = $false) {
  New-Item -ItemType Directory -Force $dirLocal | Out-Null
  $datos = [ordered]@{
    idSesion          = $idSesion
    pidConsola        = $PID
    pidBackend        = if ($null -ne $infoBackend) { $infoBackend.pid } else { $null }
    pidVentanas       = if ($null -ne $infoVentanas) { $infoVentanas.pid } else { $null }
    pidSonda          = if ($null -ne $infoSonda) { $infoSonda.pid } else { $null }
    backend           = $infoBackend
    ventanas          = $infoVentanas
    sonda             = $infoSonda
    jar               = $jar
    base              = $baseDatos
    puerto            = $puerto
    hora              = $horaInicio
    inicio            = $horaInicio
    worktree          = $raiz
    limpiezaPendiente = $limpiezaPendiente
  }
  $json = $datos | ConvertTo-Json -Depth 6
  [IO.File]::WriteAllText($archivoSesion, $json, $utf8SinBom)
}

function Leer-ArchivoSesion {
  if (!(Test-Path -LiteralPath $archivoSesion)) { return $null }
  try {
    return ([IO.File]::ReadAllText($archivoSesion, $utf8SinBom) | ConvertFrom-Json)
  } catch {
    return $null
  }
}

function Sesion-TieneId([string]$idEsperado) {
  if ([string]::IsNullOrWhiteSpace($idEsperado)) { return $false }
  $actual = Leer-ArchivoSesion
  if ($null -eq $actual -or [string]::IsNullOrWhiteSpace([string]$actual.idSesion)) { return $false }
  return [string]::Equals([string]$actual.idSesion, $idEsperado, [StringComparison]::Ordinal)
}

function Marcar-LimpiezaPendienteEnArchivo([string]$idEsperado) {
  $actual = Leer-ArchivoSesion
  if ($null -eq $actual -or [string]$actual.idSesion -ne $idEsperado) { return }
  $actual | Add-Member -NotePropertyName 'limpiezaPendiente' -NotePropertyValue $true -Force
  $json = $actual | ConvertTo-Json -Depth 6
  [IO.File]::WriteAllText($archivoSesion, $json, $utf8SinBom)
}

function Detener-ProcesoRegistrado($registroProceso, [string]$etiqueta) {
  if ($null -eq $registroProceso -or $null -eq $registroProceso.pid) {
    return "$etiqueta sin PID registrado"
  }
  $id = [int]$registroProceso.pid
  $creacionEsperada = [string]$registroProceso.creacion
  $ejecutableEsperado = [string]$registroProceso.ejecutable
  $lineaEsperada = [string]$registroProceso.lineaOrdenes
  if ([string]::IsNullOrWhiteSpace($creacionEsperada) -or
      [string]::IsNullOrWhiteSpace($ejecutableEsperado) -or
      [string]::IsNullOrWhiteSpace($lineaEsperada)) {
    return "$etiqueta (PID $id) omitido: identidad registrada incompleta"
  }

  $procVivo = Get-Process -Id $id -ErrorAction SilentlyContinue
  if ($null -eq $procVivo) {
    return "$etiqueta (PID $id) ya no existía"
  }

  $creacionActual = $null
  try {
    $creacionActual = $procVivo.StartTime.ToUniversalTime().ToString('o')
  } catch {
    if (!(Proceso-Vivo $id)) { return "$etiqueta (PID $id) ya no existía" }
    return "$etiqueta (PID $id) omitido: sin hora de creación verificable"
  }
  if ($creacionActual -ne $creacionEsperada) {
    return "$etiqueta (PID $id) omitido: su hora de creación no coincide"
  }

  $procCim = Get-CimInstance Win32_Process -Filter "ProcessId = $id" -ErrorAction SilentlyContinue
  if ($null -eq $procCim) {
    if (!(Proceso-Vivo $id)) { return "$etiqueta (PID $id) ya no existía" }
    return "$etiqueta (PID $id) omitido: sin datos CIM verificables"
  }

  $ejecutableActual = if (-not [string]::IsNullOrWhiteSpace($procCim.ExecutablePath)) {
    [string]$procCim.ExecutablePath
  } elseif (-not [string]::IsNullOrWhiteSpace($procVivo.Path)) {
    [string]$procVivo.Path
  } else {
    $null
  }
  if ([string]::IsNullOrWhiteSpace($ejecutableActual)) {
    return "$etiqueta (PID $id) omitido: sin ruta de ejecutable verificable"
  }
  if (-not [string]::Equals([IO.Path]::GetFullPath($ejecutableActual), [IO.Path]::GetFullPath($ejecutableEsperado), [StringComparison]::OrdinalIgnoreCase)) {
    return "$etiqueta (PID $id) omitido: su ejecutable no coincide"
  }

  if ([string]::IsNullOrWhiteSpace($procCim.CommandLine)) {
    return "$etiqueta (PID $id) omitido: sin línea de órdenes verificable"
  }
  if (-not [string]::Equals((Normalizar-LineaOrdenes $procCim.CommandLine), (Normalizar-LineaOrdenes $lineaEsperada), [StringComparison]::OrdinalIgnoreCase)) {
    return "$etiqueta (PID $id) omitido: su línea de órdenes no coincide"
  }

  Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
  Wait-Process -Id $id -Timeout 10 -ErrorAction SilentlyContinue
  return "$etiqueta detenido (PID $id)"
}

function Recuperar-Sesion {
  New-Item -ItemType Directory -Force $dirLocal | Out-Null
  $cerrojoRecAdquirido = $false
  $esperoOtraRecuperacion = $false
  $pidOtraRecuperacion = $null
  $sesionInicial = Leer-ArchivoSesion
  $idSesionAntesDeEsperar = if ($null -ne $sesionInicial -and -not [string]::IsNullOrWhiteSpace([string]$sesionInicial.idSesion)) {
    [string]$sesionInicial.idSesion
  } else {
    $null
  }

  try {
    for ($intento = 1; $intento -le 150; $intento++) {
      try {
        $flujoRec = [IO.File]::Open($cerrojoRecuperacion, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        try {
          $lineaRec = "pid=$PID inicio=$((Get-Date).ToString('o'))`n"
          $bytesRec = $utf8SinBom.GetBytes($lineaRec)
          $flujoRec.Write($bytesRec, 0, $bytesRec.Length)
        } finally {
          $flujoRec.Dispose()
        }
        $cerrojoRecAdquirido = $true
        break
      } catch [System.IO.IOException] {
        Start-Sleep -Milliseconds 100
        $pidRec = Obtener-PidArchivo $cerrojoRecuperacion
        if ($null -ne $pidRec -and $pidRec -ne $PID -and !(Proceso-Vivo $pidRec)) {
          Remove-Item -LiteralPath $cerrojoRecuperacion -Force -ErrorAction SilentlyContinue
          continue
        }
        $esperoOtraRecuperacion = $true
        if ($null -ne $pidRec) { $pidOtraRecuperacion = $pidRec }
        Start-Sleep -Milliseconds 100
      }
    }

    if (-not $cerrojoRecAdquirido) {
      Write-Output "Otra recuperación sigue en curso (PID '$pidOtraRecuperacion'). Se sale sin tocar nada."
      return
    }

    $sesion = Leer-ArchivoSesion
    if ($esperoOtraRecuperacion) {
      $idActual = if ($null -ne $sesion) { [string]$sesion.idSesion } else { $null }
      if ($null -eq $sesion -or $null -eq $idSesionAntesDeEsperar -or $idActual -ne $idSesionAntesDeEsperar) {
        Write-Output "Otra recuperación concurrente (PID '$pidOtraRecuperacion') ya completó la limpieza. Se sale sin tocar nada."
        return
      }
    }

    Verificar-CerrojoLibre

    if ($null -ne $sesion) {
      $idSesionObjetivo = [string]$sesion.idSesion
      if ([string]::IsNullOrWhiteSpace($idSesionObjetivo)) {
        throw 'El archivo de sesión no contiene un identificador único (idSesion). Se aborta sin tocar nada.'
      }
      if ($null -ne $sesion.pidConsola -and ([int]$sesion.pidConsola -ne $PID) -and (Proceso-Vivo ([int]$sesion.pidConsola))) {
        throw "La consola registrada en la sesión (PID $($sesion.pidConsola)) sigue viva. Se aborta sin tocar nada."
      }
    } elseif (Test-Path -LiteralPath $Cerrojo) {
      $pidCerrojo = Obtener-PidCerrojo
      throw "Existe el cerrojo $Cerrojo (PID '$pidCerrojo') sin archivo de sesión propio. Se aborta sin tocar nada."
    } else {
      Write-Output 'No hay sesión pendiente de recuperar; se sale sin tocar nada.'
      return
    }

    $resumen = [System.Collections.Generic.List[string]]::new()
    $resumen.Add((Detener-ProcesoRegistrado $sesion.backend 'backend'))
    $resumen.Add((Detener-ProcesoRegistrado $sesion.ventanas 'ventanas'))
    if ($null -ne $sesion.sonda) {
      $resumen.Add((Detener-ProcesoRegistrado $sesion.sonda 'sonda'))
    }

    if (-not (Sesion-TieneId $idSesionObjetivo)) {
      throw "El archivo de sesión cambió o fue eliminado antes de vaciar la base (idSesion esperado: $idSesionObjetivo). Se aborta sin tocar nada."
    }

    try {
      Vaciar-Base
      $resumen.Add("base $baseDatos vaciada")
    } catch {
      $errVaciado = $_
      if (Sesion-TieneId $idSesionObjetivo) {
        Marcar-LimpiezaPendienteEnArchivo $idSesionObjetivo
      }
      $mensajeFallo = ("Falló el vaciado de $baseDatos durante la recuperación: $($errVaciado.Exception.Message) " +
        "Se conservan el archivo de sesión (limpiezaPendiente=true) y el cerrojo. " +
        "Para reintentar la recuperación ejecute: pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Recuperar")
      Write-Output $mensajeFallo
      throw $mensajeFallo
    }

    if (-not (Sesion-TieneId $idSesionObjetivo)) {
      throw "El archivo de sesión cambió tras vaciar la base (idSesion esperado: $idSesionObjetivo). Se conserva el estado actual sin borrar registros ajenos."
    }

    if (Test-Path -LiteralPath $Cerrojo) {
      $pidCerrojo = Obtener-PidCerrojo
      if ($null -ne $sesion.pidConsola -and $null -ne $pidCerrojo -and ([int]$pidCerrojo -eq [int]$sesion.pidConsola) -and !(Proceso-Vivo ([int]$sesion.pidConsola))) {
        Remove-Item -LiteralPath $Cerrojo -Force
        $resumen.Add("cerrojo borrado (PID $pidCerrojo)")
      } else {
        $resumen.Add("cerrojo conservado (PID '$pidCerrojo' no coincide con la consola registrada)")
      }
    }

    if (Sesion-TieneId $idSesionObjetivo) {
      Remove-Item -LiteralPath $archivoSesion -Force
      $resumen.Add('archivo de sesión borrado')
    }

    Write-Output ('Recuperación completada: ' + ($resumen -join '; ') + '.')
  } finally {
    if ($cerrojoRecAdquirido -and (Test-Path -LiteralPath $cerrojoRecuperacion)) {
      $pidRecActual = Obtener-PidArchivo $cerrojoRecuperacion
      if ($pidRecActual -eq $PID) {
        Remove-Item -LiteralPath $cerrojoRecuperacion -Force -ErrorAction SilentlyContinue
      }
    }
  }
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
  if ((Test-Path -LiteralPath $archivoSesion) -or (Test-Path -LiteralPath $cerrojoRecuperacion)) {
    Write-Output 'Se detectó un archivo de sesión o recuperación previa; ejecutando recuperación antes de arrancar...'
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

  # 5. Asignar el propio proceso de la consola al Job Object ANTES de crear ningún proceso hijo,
  #    tomar el cerrojo de la base y escribir el archivo de sesión con identificador único (GUID).
  $trabajo = [BarberTurno.Revision.TrabajoRevision]::Crear()
  try {
    $trabajo.Asignar([System.Diagnostics.Process]::GetCurrentProcess().Handle)
    $consolaEnTrabajo = $true
  } catch {
    $trabajo.Dispose()
    $trabajo = $null
    throw "No se pudo asignar el proceso de la consola (PID $PID) al Job Object de Windows: $($_.Exception.Message). Se aborta antes de crear procesos hijos."
  }

  New-Item -ItemType Directory -Force (Split-Path -Parent $Cerrojo) | Out-Null
  $lineaCerrojo = "pid=$PID idSesion=$idSesion worktree=$raiz inicio=$horaInicio"
  $flujoCerrojo = [IO.File]::Open($Cerrojo, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
  try {
    $bytesCerrojo = $utf8SinBom.GetBytes($lineaCerrojo + "`n")
    $flujoCerrojo.Write($bytesCerrojo, 0, $bytesCerrojo.Length)
  } finally {
    $flujoCerrojo.Dispose()
  }
  $cerrojoPropio = $true

  Escribir-Sesion
  $sesionPropia = $true

  # 6. Vaciar la base y arrancar el backend (que nace ya dentro del Job Object de la consola),
  #    registrando de inmediato su identidad completa antes de esperar a que arranque.
  $basePreparada = $true
  Vaciar-Base

  $ejecutableJava = [IO.Path]::GetFullPath((Join-Path $env:JAVA_HOME 'bin\java.exe'))
  $argumentosBackend = @('-jar', $jar, '--spring.profiles.active=dev,demo', "--server.port=$puerto")
  $backend = Start-Process -FilePath $ejecutableJava -ArgumentList $argumentosBackend `
    -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -RedirectStandardOutput $registro `
    -RedirectStandardError ($registro -replace '\.log$', '-error.log')
  if (-not $trabajo.Pertenece($backend.Handle)) {
    $trabajo.Asignar($backend.Handle)
  }
  $infoBackend = Construir-InfoProceso $backend $ejecutableJava $argumentosBackend
  Escribir-Sesion
  Actualizar-InfoProcesoCim $backend.Id $infoBackend
  Escribir-Sesion
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
    # 7b. Comprobación automática de cuentas, papeles, avisos y sondas (dentro del trabajo y registradas en la sesión).
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

    $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $frontend 'tmp\navegadores'
    $ejecutableNode = [IO.Path]::GetFullPath((Get-Command node -ErrorAction Stop).Source)
    foreach ($sonda in $Sondas) {
      $rutaSonda = if (Test-Path -LiteralPath $sonda) {
        [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $sonda).Path)
      } else {
        [IO.Path]::GetFullPath((Join-Path $herramientas (Join-Path 'revision' $sonda)))
      }
      $argumentosSonda = @($rutaSonda)
      $sondaActiva = Start-Process -FilePath $ejecutableNode -ArgumentList $argumentosSonda `
        -WorkingDirectory $frontend -NoNewWindow -PassThru
      if (-not $trabajo.Pertenece($sondaActiva.Handle)) {
        $trabajo.Asignar($sondaActiva.Handle)
      }
      $infoSonda = Construir-InfoProceso $sondaActiva $ejecutableNode $argumentosSonda
      Escribir-Sesion
      Actualizar-InfoProcesoCim $sondaActiva.Id $infoSonda
      Escribir-Sesion
      $sondaActiva.WaitForExit()
      $codigoSonda = $sondaActiva.ExitCode
      $sondaActiva = $null
      $infoSonda = $null
      Escribir-Sesion
      if ($codigoSonda -ne 0) { throw "La sonda $sonda no terminó correctamente (código $codigoSonda)." }
    }
  } else {
    # 7c. Ventanas interactivas dentro del Job Object; la revisión termina al cerrarlas.
    $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $frontend 'tmp\navegadores'
    $ejecutableNode = [IO.Path]::GetFullPath((Get-Command node -ErrorAction Stop).Source)
    $scriptVentanas = [IO.Path]::GetFullPath((Join-Path $herramientas 'revision\ventanas.mjs'))
    $argumentosVentanas = @($scriptVentanas)
    $ventanas = Start-Process -FilePath $ejecutableNode -ArgumentList $argumentosVentanas `
      -WorkingDirectory $frontend -NoNewWindow -PassThru
    if (-not $trabajo.Pertenece($ventanas.Handle)) {
      $trabajo.Asignar($ventanas.Handle)
    }
    $infoVentanas = Construir-InfoProceso $ventanas $ejecutableNode $argumentosVentanas
    Escribir-Sesion
    Actualizar-InfoProcesoCim $ventanas.Id $infoVentanas
    Escribir-Sesion
    $ventanas.WaitForExit()
    if ($ventanas.ExitCode -ne 0) { throw "El proceso de ventanas terminó con código $($ventanas.ExitCode)." }
  }
} finally {
  try {
    if ($sondaActiva -and !$sondaActiva.HasExited) {
      Stop-Process -Id $sondaActiva.Id -Force -ErrorAction SilentlyContinue
      $sondaActiva.WaitForExit()
    }
    if ($ventanas -and !$ventanas.HasExited) {
      Stop-Process -Id $ventanas.Id -Force -ErrorAction SilentlyContinue
      $ventanas.WaitForExit()
    }
    if ($backend -and !$backend.HasExited) {
      Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue
      $backend.WaitForExit()
      Write-Output ('Backend detenido: PID ' + $backend.Id)
    }
    if ($trabajo -and -not $consolaEnTrabajo) {
      $trabajo.Dispose()
      $trabajo = $null
    }

    $vaciadoConfirmado = $false
    if ($basePreparada) {
      if (-not (Sesion-TieneId $idSesion)) {
        throw "El archivo de sesión ya no coincide con la sesión actual ($idSesion); se aborta sin vaciar ni borrar registros ajenos."
      }
      try {
        Vaciar-Base
        $vaciadoConfirmado = $true
        Write-Output "$baseDatos vaciada."
      } catch {
        $errCierre = $_
        if ($sesionPropia -and (Sesion-TieneId $idSesion)) {
          $infoBackend = $null
          $infoVentanas = $null
          $infoSonda = $null
          Escribir-Sesion $true
        }
        $mensajeCierre = ("No se pudo vaciar $baseDatos al cerrar la sesión: $($errCierre.Exception.Message) " +
          "Se conservan el archivo de sesión (limpiezaPendiente=true) y el cerrojo. " +
          "Para completar la limpieza ejecute: pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Recuperar")
        Write-Output $mensajeCierre
        throw $mensajeCierre
      }
    } else {
      $vaciadoConfirmado = $true
    }

    if ($vaciadoConfirmado) {
      if ($sesionPropia -and (Sesion-TieneId $idSesion)) {
        if ($cerrojoPropio -and (Test-Path -LiteralPath $Cerrojo) -and ((Obtener-PidCerrojo) -eq $PID)) {
          Remove-Item -LiteralPath $Cerrojo -Force -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $archivoSesion -Force -ErrorAction SilentlyContinue
      } elseif ($cerrojoPropio -and -not $sesionPropia -and (Test-Path -LiteralPath $Cerrojo) -and ((Obtener-PidCerrojo) -eq $PID)) {
        Remove-Item -LiteralPath $Cerrojo -Force -ErrorAction SilentlyContinue
      }
    }
  } finally {
    Limpiar-EntornoProceso
  }
}
