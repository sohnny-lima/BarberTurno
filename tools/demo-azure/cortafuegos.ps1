#requires -Version 7.2
<# .SYNOPSIS
Añade únicamente las IP salientes de la demo que aún no estén cubiertas por el cortafuegos.
#>
[CmdletBinding()]
param(
    [string]$Suscripcion, [string]$Az = 'D:\Herramientas\azure-cli\bin\az.cmd',
    [switch]$Simular, [string]$Inventario
)
. "$PSScriptRoot/comun.ps1"
if ($Inventario -and -not $Simular) { throw '-Inventario solo se admite con -Simular; la ejecución usa siempre el estado real.' }
if ($Simular) {
    if (-not $Inventario) {
        Write-Output 'SIMULAR sin red: consultar outboundIpAddresses y reglas, comparar intervalos y crear solo demo-v2-<IP> sin cobertura.'
        Write-Output 'Para enumerar acciones exactas sin Azure, indique -Inventario con el JSON descrito en el README.'
        return
    }
    $datos = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Inventario).Path) | ConvertFrom-Json
    $ips = @(([string]$datos.outboundIpAddresses).Split(',', [StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object { $_.Trim() })
    $reglas = @($datos.reglas)
} else {
    $app = Assert-AppDemo $Az $Suscripcion
    # possibleOutboundIpAddresses (superconjunto): la app puede salir por cualquiera de ellas, no solo por las actuales.
    $ips = @(([string]$app.possibleOutboundIpAddresses).Split(',', [StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object { $_.Trim() })
    $reglas = @((Invoke-AzDemo $Az @('postgres', 'flexible-server', 'firewall-rule', 'list',
        '-g', 'rg-barberturno-prod', '-s', 'pg-barberturno-202610', '-o', 'json')) | ConvertFrom-Json)
}
if ($ips.Count -eq 0) { throw 'La web app no devolvió IP salientes; no se modifica el cortafuegos.' }
$faltantes = @(Get-ReglasFaltantesDemo $ips $reglas)
foreach ($regla in $faltantes) {
    if ($Simular) { Write-Output "SIMULAR: crear $($regla.name) [$($regla.startIpAddress)]."; continue }
    [void](Invoke-AzDemo $Az @('postgres', 'flexible-server', 'firewall-rule', 'create',
        '-g', 'rg-barberturno-prod', '-s', 'pg-barberturno-202610', '-n', $regla.name,
        '--start-ip-address', $regla.startIpAddress, '--end-ip-address', $regla.endIpAddress, '-o', 'none'))
    Write-Output "Creada $($regla.name)."
}
Write-Output "Reglas que faltaban: $($faltantes.Count). Las demás se conservan."
