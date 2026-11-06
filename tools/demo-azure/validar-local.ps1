#requires -Version 7.2
<# .SYNOPSIS
Valida sintaxis y simulaciones con datos sintéticos; no usa Azure, Java, PostgreSQL ni .local.
#>
[CmdletBinding()]
param()
. "$PSScriptRoot/comun.ps1"
$script:casos = 0
function Assert-Caso([bool]$Condicion, [string]$Nombre) {
    if (-not $Condicion) { throw "Fallo: $Nombre" }
    $script:casos++
    Write-Output "OK: $Nombre"
}
function Assert-Rechazo([scriptblock]$Accion, [string]$Nombre) {
    $rechazado = $false
    try { & $Accion | Out-Null } catch { $rechazado = $true }
    Assert-Caso $rechazado $Nombre
}
foreach ($archivo in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
    $tokens = $null; $errores = $null
    [void][Management.Automation.Language.Parser]::ParseFile($archivo.FullName, [ref]$tokens, [ref]$errores)
    Assert-Caso ($errores.Count -eq 0) "Sintaxis $($archivo.Name)"
}
# Ejecutables inexistentes: si una simulación intenta lanzar un proceso, la validación falla.
foreach ($nombre in @('preparar-base', 'sembrar', 'configurar-app', 'cortafuegos', 'retirar')) {
    $parametros = @{ Simular = $true }
    if ($nombre -in @('preparar-base', 'retirar')) { $parametros.Psql = 'Z:\no-existe\psql.exe' }
    if ($nombre -in @('configurar-app', 'cortafuegos', 'retirar')) { $parametros.Az = 'Z:\no-existe\az.cmd' }
    if ($nombre -eq 'sembrar') { $parametros.Java = 'Z:\no-existe\java.exe'; $parametros.Jar = 'Z:\no-existe\app.jar' }
    if ($nombre -ne 'cortafuegos') { $parametros.Secretos = 'Z:\no-existe\secretos' }
    $salida = & (Join-Path $PSScriptRoot "$nombre.ps1") @parametros
    Assert-Caso (($salida -join "`n") -match 'SIMULAR') "Simulación sin dependencias: $nombre"
}
$salida = & "$PSScriptRoot/retirar.ps1" -Az 'Z:\no-existe\az.cmd' -Psql 'Z:\no-existe\psql.exe'
Assert-Caso (($salida -join "`n") -match 'SIMULAR') 'Retirada sin confirmar solo simula'
$salida = & "$PSScriptRoot/retirar.ps1" -Confirmar -Simular -Az 'Z:\no-existe\az.cmd'
Assert-Caso (($salida -join "`n") -match 'SIMULAR') 'Simular prevalece sobre Confirmar'
$salida = & "$PSScriptRoot/cortafuegos.ps1" -Simular -Inventario "$PSScriptRoot/inventario-ejemplo.json"
Assert-Caso (($salida -join "`n") -match 'demo-v2-203-0-113-7' -and ($salida -join "`n") -match 'faltaban: 1') 'IP exacta, intervalo y duplicados'
Assert-Rechazo { & "$PSScriptRoot/preparar-base.ps1" -Simular -Base 'barberturno_prod' } 'Rechaza base v1 antes de acceder a secretos'
Assert-Rechazo { & "$PSScriptRoot/sembrar.ps1" -Simular -RolAplicacion 'barberturno_app' } 'Rechaza rol v1'
Assert-Rechazo { & "$PSScriptRoot/retirar.ps1" -Simular -RolMigraciones 'postgres' } 'Rechaza rol administrativo'
Assert-Rechazo { & "$PSScriptRoot/configurar-app.ps1" -Simular -TwaPaquete 'pe.ejemplo.demo' } 'TWA exige ambos parámetros'
Assert-Rechazo { & "$PSScriptRoot/cortafuegos.ps1" -Inventario "$PSScriptRoot/inventario-ejemplo.json" } 'Inventario ficticio prohibido en ejecución real'
Assert-Rechazo { Get-ReglasFaltantesDemo @('999.0.0.1') @() } 'Rechaza IPv4 inválida'
Assert-Rechazo { Get-ReglasFaltantesDemo @('::1') @() } 'Rechaza IPv6'
Assert-Rechazo { Get-ReglasFaltantesDemo @('203.0.113.7') @([pscustomobject]@{
    name = 'demo-v2-203-0-113-7'; startIpAddress = '192.0.2.5'; endIpAddress = '192.0.2.5'
}) } 'Rechaza colisión de nombres sin cambiar la regla'
$vacias = @(Get-ReglasFaltantesDemo @('192.0.2.1') @([pscustomobject]@{
    name = 'cubierta'; startIpAddress = '192.0.2.1'; endIpAddress = '192.0.2.1'
}))
Assert-Caso ($vacias.Count -eq 0) 'Cortafuegos idempotente cuando la IP está cubierta'
# Verificar el aislamiento por inspección de ProcessStartInfo, sin iniciar el proceso.
$anterior = [Environment]::GetEnvironmentVariable('BT_DEMO_PASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('BT_DEMO_PASSWORD', 'sintetica-no-se-imprime', 'Process')
    $proceso = New-ProcesoDemo 'no-ejecutar' @('argumento') @{ BT_DB_USER = 'rol-sintetico' }
    try {
        Assert-Caso (-not $proceso.StartInfo.Environment.ContainsKey('BT_DEMO_PASSWORD')) 'El hijo no hereda BT_DEMO_PASSWORD'
        Assert-Caso ($proceso.StartInfo.Environment['BT_DB_USER'] -eq 'rol-sintetico') 'Variables explícitas solo en el hijo'
        Assert-Caso ($proceso.StartInfo.CreateNoWindow -and -not $proceso.StartInfo.UseShellExecute) 'Hijo oculto y sin shell implícito'
    } finally { $proceso.Dispose() }
} finally { [Environment]::SetEnvironmentVariable('BT_DEMO_PASSWORD', $anterior, 'Process') }
# Vector publicado en RFC 7677 §3: comprobar ServerKey y StoredKey sin servidor ni credenciales.
# https://www.rfc-editor.org/rfc/rfc7677#section-3
$verificador = Get-VerificadorScram 'pencil' ([Convert]::FromBase64String('W22ZaJ0SNY7soEsUEjb6gQ=='))
$partes = $verificador.Split('$')
$claves = $partes[2].Split(':')
$nonce = 'rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0'
$autenticacion = 'n=user,r=rOprNGfwEbeRWgbNEkqO,r=' + $nonce + ',s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096,c=biws,r=' + $nonce
$bytesAutenticacion = [Text.Encoding]::UTF8.GetBytes($autenticacion)
$hmac = [Security.Cryptography.HMACSHA256]::new([Convert]::FromBase64String($claves[1]))
try { $firma = [Convert]::ToBase64String($hmac.ComputeHash($bytesAutenticacion)) } finally { $hmac.Dispose() }
Assert-Caso ($firma -ceq '6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=') 'SCRAM RFC 7677: firma del servidor'
$hmac = [Security.Cryptography.HMACSHA256]::new([Convert]::FromBase64String($claves[0]))
try { $firmaCliente = $hmac.ComputeHash($bytesAutenticacion) } finally { $hmac.Dispose() }
$pruebaCliente = [Convert]::FromBase64String('dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=')
$claveCliente = [byte[]]::new(32)
for ($i = 0; $i -lt 32; $i++) { $claveCliente[$i] = $pruebaCliente[$i] -bxor $firmaCliente[$i] }
$almacenada = [Convert]::ToBase64String([Security.Cryptography.SHA256]::HashData($claveCliente))
Assert-Caso ($almacenada -ceq $claves[0]) 'SCRAM RFC 7677: verificación de la prueba del cliente'
$claveAleatoria = New-ClaveDemo
Assert-Caso ($claveAleatoria -cmatch '^[A-Za-z0-9]{32}$' -and $claveAleatoria -cmatch '[A-Z]' -and
    $claveAleatoria -cmatch '[a-z]' -and $claveAleatoria -match '[0-9]') 'Claves aleatorias cumplen longitud y composición'
Assert-Caso ((Get-VerificadorScram $claveAleatoria) -cne (Get-VerificadorScram $claveAleatoria)) 'SCRAM usa una sal nueva por cálculo'
# Ensayo exclusivamente local del paso por cmd.exe, sin invocar az.cmd ni archivos privados.
$cmdLocal = Join-Path $env:SystemRoot 'System32/cmd.exe'
$linea = '""' + $cmdLocal + '" /d /c echo "prueba (local)" "@archivo""'
$salidaCmd = Invoke-ProcesoDemo $env:ComSpec @() -LineaCmd $linea
Assert-Caso ($salidaCmd.Trim() -ceq '"prueba (local)" "@archivo"') 'cmd conserva comillas, paréntesis y @archivo'
Assert-Rechazo { Invoke-AzDemo 'Z:\no-existe\az.cmd' @('argumento|inseguro') } 'Rechaza metacaracteres antes de lanzar cmd'
Write-Output "Resultado: $script:casos comprobaciones correctas. Sin red, secretos privados ni objetos persistentes."
