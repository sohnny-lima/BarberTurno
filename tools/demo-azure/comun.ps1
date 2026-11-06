#requires -Version 7.2
# Utilidades privadas de los scripts de demo. Importar este archivo no ejecuta procesos ni lee secretos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-DestinoDemo {
    param([string]$Base, [string]$RolMigraciones, [string]$RolAplicacion)
    if ($Base -cne 'barberturno_v2_demo' -or $RolMigraciones -cne 'bt_v2_demo' -or
        $RolAplicacion -cne 'bt_v2_demo_app') { throw 'Solo se admiten los nombres fijos de la demo v2.' }
}

function Initialize-DirectorioSecretos {
    param([string]$Secretos)
    $ruta = [IO.Path]::GetFullPath($Secretos)
    $repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    if ($ruta.Equals($repo, [StringComparison]::OrdinalIgnoreCase) -or
        $ruta.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'El directorio de secretos debe quedar fuera de este repositorio.'
    }
    [void][IO.Directory]::CreateDirectory($ruta)
    return $ruta
}

function New-ClaveDemo {
    # Muestreo sin sesgo; garantiza letras y dígitos y cumple RN-25 (32 caracteres ASCII).
    $alfabeto = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    do {
        $clave = -join (1..32 | ForEach-Object {
            $alfabeto[[Security.Cryptography.RandomNumberGenerator]::GetInt32($alfabeto.Length)]
        })
    } until ($clave -cmatch '[A-Z]' -and $clave -cmatch '[a-z]' -and $clave -match '[0-9]')
    return $clave
}

function Get-SecretoDemo {
    param([string]$Secretos, [string]$Nombre, [switch]$Crear, [switch]$Jwt)
    $ruta = Join-Path $Secretos $Nombre
    if (-not [IO.File]::Exists($ruta)) {
        if (-not $Crear) { throw "Falta el archivo $Nombre; ejecute primero el paso correspondiente." }
        $valor = if ($Jwt) { [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)) }
                 else { New-ClaveDemo }
        # CreateNew impide reemplazar un secreto incluso ante ejecuciones simultáneas.
        $flujo = [IO.File]::Open($ruta, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $bytes = [Text.Encoding]::UTF8.GetBytes($valor); $flujo.Write($bytes) } finally { $flujo.Dispose() }
    }
    $valor = [IO.File]::ReadAllText($ruta).TrimEnd("`r", "`n")
    if ([string]::IsNullOrWhiteSpace($valor) -or $valor.Contains("`n") -or $valor.Contains("`r")) {
        throw "El archivo $Nombre debe contener exactamente una línea no vacía."
    }
    if ($Jwt) {
        try { $longitud = [Convert]::FromBase64String($valor).Length } catch { throw "Formato Base64 inválido en $Nombre." }
        if ($longitud -ne 48) { throw "Se requieren 48 bytes en $Nombre." }
    } elseif ($Nombre -ne 'pg-admin.secreto' -and
        ($valor -cnotmatch '^[A-Za-z0-9]{20,72}$' -or $valor -cnotmatch '[A-Z]' -or
         $valor -cnotmatch '[a-z]' -or $valor -notmatch '[0-9]')) { throw "Formato de contraseña inválido en $Nombre." }
    return $valor
}

function Get-VerificadorScram {
    param([string]$Clave, [byte[]]$Sal = [Security.Cryptography.RandomNumberGenerator]::GetBytes(16))
    # Duplicado de tools/preparar-entorno-v2.ps1 (RFC 5802/7677), sin cambiar aquel script.
    # Solo se generan claves ASCII: no es necesaria la normalización SASLprep.
    # La sal explícita solo sirve al vector de prueba RFC; las llamadas reales usan el valor aleatorio.
    if ($Sal.Length -ne 16) { throw 'La sal SCRAM debe contener 16 bytes.' }
    $kdf = [Security.Cryptography.Rfc2898DeriveBytes]::new(
        [Text.Encoding]::UTF8.GetBytes($Clave), $sal, 4096, [Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        $salada = $kdf.GetBytes(32)
        $hmac = [Security.Cryptography.HMACSHA256]::new($salada)
        try {
            $cliente = $hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes('Client Key'))
            $servidor = $hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes('Server Key'))
            $almacenada = [Security.Cryptography.SHA256]::HashData($cliente)
            return 'SCRAM-SHA-256$4096:' + [Convert]::ToBase64String($sal) + '$' +
                [Convert]::ToBase64String($almacenada) + ':' + [Convert]::ToBase64String($servidor)
        } finally { $hmac.Dispose() }
    } finally { $kdf.Dispose() }
}

function New-ProcesoDemo {
    param([string]$Ejecutable, [string[]]$Argumentos, [hashtable]$Entorno = @{}, [string]$Directorio)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Ejecutable
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.RedirectStandardInput = $true
    $info.StandardOutputEncoding = [Text.Encoding]::UTF8
    $info.StandardErrorEncoding = [Text.Encoding]::UTF8
    if ($Directorio) { $info.WorkingDirectory = $Directorio }
    foreach ($argumento in $Argumentos) { $info.ArgumentList.Add($argumento) }
    # Evitar configuración heredada, importaciones de .local y opciones que impriman secretos.
    foreach ($nombre in @($info.Environment.Keys)) {
        if ($nombre -match '^(BT_|BARBERTURNO_|SPRING_|PG|LOGGING_|MANAGEMENT_|SERVER_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|JAVA_OPTS$)') {
            [void]$info.Environment.Remove($nombre)
        }
    }
    foreach ($nombre in $Entorno.Keys) { $info.Environment[$nombre] = [string]$Entorno[$nombre] }
    return [Diagnostics.Process]::new() | ForEach-Object { $_.StartInfo = $info; $_ }
}

function Get-MotivoSeguroDemo {
    param([string]$ErrorTexto)
    $propio = [regex]::Match($ErrorTexto, "(?m)ERROR:\s+((?:Se requiere PostgreSQL 18|Rol demo con atributos|Un rol demo|Membresía inesperada|La base demo|Los roles demo|El rol de ejecución|Hay dependencias|Quedan conexiones|Verificadores SCRAM)[^\r\n]{0,120})")
    if ($propio.Success) { return $propio.Groups[1].Value }
    if ($ErrorTexto -match '(?i)password authentication failed|autentificaci.n password fall|autenticaci.n.*fall') { return 'contraseña rechazada por el servidor' }
    if ($ErrorTexto -match '(?i)no password supplied|no se entreg. contrase') { return 'falta la contraseña' }
    if ($ErrorTexto -match '(?i)pg_hba\.conf|timeout expired|timed out|tiempo de espera|could not connect|no se pudo conectar|could not translate host|refused|rehus|en ejecuci') { return 'sin conexión con el servidor (red o cortafuegos)' }
    if ($ErrorTexto -match '(?i)certificate|certificado|SSL') { return 'error de TLS o de certificado' }
    if ($ErrorTexto -match '(?i)permission denied|permiso denegado|must be owner|debe ser due') { return 'permiso insuficiente en el servidor' }
    return 'motivo no clasificado'
}

function Invoke-ProcesoDemo {
    param([string]$Ejecutable, [string[]]$Argumentos, [hashtable]$Entorno = @{},
        [string]$Entrada = '', [int]$Segundos = 120, [string]$LineaCmd = '')
    $proceso = New-ProcesoDemo $Ejecutable $Argumentos $Entorno
    # cmd.exe no interpreta los escapes de comillas de CommandLineToArgvW que usa ArgumentList.
    # Su línea /s /c ya viene validada y entrecomillada por Invoke-AzDemo.
    if ($LineaCmd) {
        if ($Argumentos.Count -gt 0 -or $Ejecutable -ne $env:ComSpec) { throw 'La línea cmd requiere cmd.exe y Argumentos vacío.' }
        $proceso.StartInfo.Arguments = '/d /s /c ' + $LineaCmd
    }
    try {
        [void]$proceso.Start()
        $salida = $proceso.StandardOutput.ReadToEndAsync()
        $errorSalida = $proceso.StandardError.ReadToEndAsync()
        $proceso.StandardInput.Write($Entrada)
        $proceso.StandardInput.Close()
        if (-not $proceso.WaitForExit($Segundos * 1000)) {
            $proceso.Kill($true); $proceso.WaitForExit(); throw 'El proceso excedió el tiempo permitido.'
        }
        # No reenviar stderr: psql/CLI podrían incluir configuración o valores SQL. Solo se informa una
        # categoría fija o uno de los mensajes RAISE propios de estos scripts.
        $texto = $salida.GetAwaiter().GetResult()
        $errorTexto = $errorSalida.GetAwaiter().GetResult()
        if ($proceso.ExitCode -ne 0) {
            throw "El proceso falló (código $($proceso.ExitCode)): $(Get-MotivoSeguroDemo $errorTexto). Salida suprimida por seguridad."
        }
        return $texto
    } finally {
        if ($proceso.Id -and -not $proceso.HasExited) { $proceso.Kill($true); $proceso.WaitForExit() }
        $proceso.Dispose()
    }
}

function Invoke-AzDemo {
    param([string]$Az, [string[]]$Argumentos)
    # az.cmd necesita cmd.exe. Todos los argumentos se entrecomillan y se rechazan metacaracteres.
    $todos = @($Az) + $Argumentos + @('--only-show-errors')
    if ($todos | Where-Object { $_ -match '["%!^&|<>\r\n]' }) { throw 'Argumento no seguro para az.cmd.' }
    $linea = '"' + (($todos | ForEach-Object { '"' + $_ + '"' }) -join ' ') + '"'
    return Invoke-ProcesoDemo $env:ComSpec @() -LineaCmd $linea -Segundos 600
}

function Get-JdbcDemo {
    param([string]$Base)
    return "jdbc:postgresql://pg-barberturno-202610.postgres.database.azure.com:5432/${Base}?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"
}

function Get-ClaveAdminPgDemo {
    param([string]$Secretos)
    # Preferencia: escribirla oculta en una consola interactiva. Así no queda en disco y solo vive en la
    # memoria de este proceso y en PGPASSWORD del psql hijo. El archivo queda como alternativa.
    if ([IO.File]::Exists((Join-Path $Secretos 'pg-admin.secreto'))) { return Get-SecretoDemo $Secretos 'pg-admin.secreto' }
    if (-not [Environment]::UserInteractive -or [Console]::IsInputRedirected) {
        throw 'Falta la contraseña de btadmin: ejecute el script en una consola interactiva.'
    }
    $segura = Read-Host 'Contraseña de btadmin (no se muestra)' -AsSecureString
    $clave = [Net.NetworkCredential]::new('', $segura).Password
    $segura.Dispose()
    if ([string]::IsNullOrWhiteSpace($clave) -or $clave.Contains("`n") -or $clave.Contains("`r")) {
        throw 'La contraseña de btadmin no puede estar vacía.'
    }
    return $clave
}

function Get-EntornoPgDemo {
    param([string]$Secretos, [string]$Psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe',
        [securestring]$ClaveAdmin)
    $entorno = @{
        PGHOST = 'pg-barberturno-202610.postgres.database.azure.com'; PGPORT = '5432'
        PGUSER = 'btadmin'; PGDATABASE = 'postgres'; PGSSLMODE = 'verify-full'
        PGSSLROOTCERT = Join-Path $Secretos 'azure-postgresql-raices.pem'
        PGCONNECT_TIMEOUT = '20'; PGCLIENTENCODING = 'UTF8'
        PGOPTIONS = '-c statement_timeout=60000 -c lock_timeout=5000'
        PGAPPNAME = 'barberturno-v2-demo-provision'
    }
    if ($ClaveAdmin) {
        # Clave recibida en memoria (p. ej., recién restablecida): se reintenta sin pedirla mientras Azure la propaga.
        $entorno.PGPASSWORD = [Net.NetworkCredential]::new('', $ClaveAdmin).Password
        for ($i = 1; $i -le 6; $i++) {
            try { [void](Invoke-PsqlDemo $Psql $entorno 'SELECT 1;'); return $entorno }
            catch {
                if ($i -eq 6 -or $_.Exception.Message -notmatch 'contraseña rechazada') { throw }
                Start-Sleep -Seconds 20
            }
        }
    }
    # Hasta tres intentos si la clave se escribe en la consola: se prueba con un SELECT 1 antes de seguir.
    $intentos = if ([IO.File]::Exists((Join-Path $Secretos 'pg-admin.secreto'))) { 1 } else { 3 }
    for ($i = 1; $i -le $intentos; $i++) {
        $entorno.PGPASSWORD = Get-ClaveAdminPgDemo $Secretos
        try { [void](Invoke-PsqlDemo $Psql $entorno 'SELECT 1;'); return $entorno }
        catch {
            if ($i -eq $intentos -or $_.Exception.Message -notmatch 'contraseña rechazada') { throw }
            Write-Host "Contraseña rechazada (intento $i de $intentos). Vuelva a escribirla." -ForegroundColor Yellow
        }
    }
}

function Invoke-PsqlDemo {
    param([string]$Psql, [hashtable]$Entorno, [string]$Sql, [string[]]$Extra = @())
    return Invoke-ProcesoDemo $Psql (@('-X', '-w', '--set=ON_ERROR_STOP=1', '--set=VERBOSITY=terse') + $Extra) $Entorno -Entrada $Sql
}

function Get-RaicesAzureDemo {
    param([string]$Secretos, [switch]$Descargar)
    # Raíces y URL oficiales: https://learn.microsoft.com/en-us/azure/security/fundamentals/azure-certificate-authority-details
    # SHA-256 G2: https://knowledge.digicert.com/general-information/digicert-trusted-root-authority-certificates
    # SHA-256 Microsoft: https://ccadb.my.salesforce-sites.com/microsoft/includedcacertificatereportformsft
    $fuentes = @(
        @{ Nombre = 'digicert-g2.crt'; Url = 'https://cacerts.digicert.com/DigiCertGlobalRootG2.crt'
           Hash = 'CB3CCBB76031E5E0138F8DD39A23F9DE47FFC35E43C1144CEA27D46A5AB1CB5F' },
        @{ Nombre = 'microsoft-rsa-2017.crt'; Url = 'https://www.microsoft.com/pkiops/certs/Microsoft%20RSA%20Root%20Certificate%20Authority%202017.crt'
           Hash = 'C741F70F4B2A8D88BF2E71C14122EF53EF10EBA0CFA5E64CFA20F418853073E0' }
    )
    $pem = ''
    foreach ($fuente in $fuentes) {
        $ruta = Join-Path $Secretos $fuente.Nombre
        if (-not [IO.File]::Exists($ruta)) {
            if (-not $Descargar) { throw 'Faltan certificados; ejecute preparar-base.ps1.' }
            # Descargar en memoria y persistir solo tras comprobar el DER del certificado.
            $cliente = [Net.Http.HttpClient]::new()
            try { $bytes = $cliente.GetByteArrayAsync($fuente.Url).GetAwaiter().GetResult() }
            finally { $cliente.Dispose() }
        } else { $bytes = [IO.File]::ReadAllBytes($ruta) }
        $certificado = [Security.Cryptography.X509Certificates.X509Certificate2]::new([byte[]]$bytes)
        try {
            if ($certificado.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256) -cne $fuente.Hash) {
                throw 'Huella SHA-256 de una raíz inesperada; no se usará ni guardará.'
            }
            if ([DateTime]::UtcNow -gt $certificado.NotAfter.ToUniversalTime()) { throw 'Certificado raíz caducado.' }
            $pem += $certificado.ExportCertificatePem() + "`n"
            if (-not [IO.File]::Exists($ruta)) { [IO.File]::WriteAllBytes($ruta, $certificado.RawData) }
        } finally { $certificado.Dispose() }
    }
    $destino = Join-Path $Secretos 'azure-postgresql-raices.pem'
    [IO.File]::WriteAllText($destino, $pem, [Text.UTF8Encoding]::new($false))
    return $destino
}

function Assert-SuscripcionDemo {
    param([string]$Az, [string]$Suscripcion)
    if (-not [guid]::TryParse($Suscripcion, [ref]([guid]::Empty))) { throw 'Indique -Suscripcion con el UUID revisado por el responsable.' }
    $cuenta = (Invoke-AzDemo $Az @('account', 'show', '-o', 'json')) | ConvertFrom-Json
    if ($cuenta.id -ne $Suscripcion -or $cuenta.state -ne 'Enabled') { throw 'La suscripción activa no coincide o no está habilitada.' }
}

function Assert-AppDemo {
    param([string]$Az, [string]$Suscripcion)
    Assert-SuscripcionDemo $Az $Suscripcion
    $app = (Invoke-AzDemo $Az @('webapp', 'show', '-g', 'rg-barberturno-v2-demo', '-n', 'barberturno-v2-demo-202611', '-o', 'json')) | ConvertFrom-Json
    $plan = (Invoke-AzDemo $Az @('appservice', 'plan', 'show', '-g', 'rg-barberturno-v2-demo', '-n', 'plan-barberturno-v2-demo', '-o', 'json')) | ConvertFrom-Json
    # La CLI 2.92 anida «reserved» en properties y expone appServicePlanId (con otra capitalización que plan.id).
    $linux = $plan.kind -eq 'linux' -and $plan.properties.reserved -eq $true
    $mismoPlan = [string]::Equals([string]$app.appServicePlanId, [string]$plan.id, [StringComparison]::OrdinalIgnoreCase)
    if ($plan.sku.name -ne 'F1' -or -not $linux -or -not $mismoPlan -or -not $app.httpsOnly) {
        throw 'La demo debe usar su plan Linux F1 aislado y HTTPS obligatorio.'
    }
    return $app
}

function ConvertTo-NumeroIpv4 {
    param([string]$Ip)
    if ($Ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw 'Se requiere una dirección IPv4 decimal.' }
    $numero = [uint64]0
    foreach ($parte in $Ip.Split('.')) {
        if ([int]$parte -gt 255) { throw 'Dirección IPv4 fuera de rango.' }
        $numero = $numero * 256 + [int]$parte
    }
    return $numero
}

function Get-ReglasFaltantesDemo {
    param([string[]]$Ips, [object[]]$Reglas)
    foreach ($ip in ($Ips | Sort-Object -Unique)) {
        $numero = ConvertTo-NumeroIpv4 $ip
        $cubierta = $false
        foreach ($regla in $Reglas) {
            $inicio = ConvertTo-NumeroIpv4 $regla.startIpAddress
            $fin = ConvertTo-NumeroIpv4 $regla.endIpAddress
            if ($inicio -gt $fin) { throw 'Inventario con intervalo de IP inválido.' }
            if ($numero -ge $inicio -and $numero -le $fin) { $cubierta = $true }
        }
        if (-not $cubierta) {
            $nombre = 'demo-v2-' + $ip.Replace('.', '-')
            if ($Reglas | Where-Object { $_.name -eq $nombre }) { throw 'Ya existe una regla con ese nombre y otra IP; no se modifica.' }
            [pscustomobject]@{ name = $nombre; startIpAddress = $ip; endIpAddress = $ip }
        }
    }
}
