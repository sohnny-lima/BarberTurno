#requires -Version 7.2
<# .SYNOPSIS
Retira solo la demo con -Confirmar; por defecto describe las acciones sin red ni lecturas de secretos.
#>
[CmdletBinding()]
param(
    [string]$Secretos = 'D:\Proyectos\BarberTurno\.local\demo-azure', [string]$Suscripcion,
    [string]$Base = 'barberturno_v2_demo', [string]$RolMigraciones = 'bt_v2_demo',
    [string]$RolAplicacion = 'bt_v2_demo_app',
    [string]$Psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe',
    [string]$Az = 'D:\Herramientas\azure-cli\bin\az.cmd', [switch]$Confirmar, [switch]$Simular
)
. "$PSScriptRoot/comun.ps1"
Assert-DestinoDemo $Base $RolMigraciones $RolAplicacion
if ($Simular -or -not $Confirmar) {
    Write-Output 'SIMULAR sin red: comprobar inventario del grupo demo y propietarios/dependencias SQL; detener la app demo.'
    Write-Output 'SIMULAR: borrar barberturno_v2_demo y bt_v2_demo_app/bt_v2_demo; borrar solo reglas demo-v2-* y rg-barberturno-v2-demo.'
    Write-Output 'No se borran archivos locales. Para ejecutar: -Confirmar -Suscripcion <UUID>; -Simular siempre prevalece.'
    return
}
Assert-SuscripcionDemo $Az $Suscripcion
# Antes de destruir: rechazar recursos inesperados dentro del grupo.
$grupoExiste = ((Invoke-AzDemo $Az @('group', 'exists', '-n', 'rg-barberturno-v2-demo', '-o', 'tsv')).Trim() -eq 'true')
$appExiste = $false
if ($grupoExiste) {
    $recursos = @((Invoke-AzDemo $Az @('resource', 'list', '-g', 'rg-barberturno-v2-demo', '-o', 'json')) | ConvertFrom-Json)
    foreach ($recurso in $recursos) {
        if ($recurso.type -eq 'Microsoft.Web/sites' -and $recurso.name -eq 'barberturno-v2-demo-202611') { $appExiste = $true; continue }
        if ($recurso.type -eq 'Microsoft.Web/serverfarms' -and $recurso.name -eq 'plan-barberturno-v2-demo') { continue }
        throw 'El grupo demo contiene recursos inesperados; no se borra nada.'
    }
}
$reglas = @((Invoke-AzDemo $Az @('postgres', 'flexible-server', 'firewall-rule', 'list',
    '-g', 'rg-barberturno-prod', '-s', 'pg-barberturno-202610', '-o', 'json')) | ConvertFrom-Json)
$demoReglas = @($reglas | Where-Object { $_.name -clike 'demo-v2-*' })
foreach ($regla in $demoReglas) {
    # Nombre y rango exactos del creador: un nombre extraño no permite borrar un rango ajeno.
    [void](ConvertTo-NumeroIpv4 $regla.startIpAddress)
    if ($regla.startIpAddress -ne $regla.endIpAddress -or $regla.name -cne ('demo-v2-' + $regla.startIpAddress.Replace('.', '-'))) {
        throw 'Una regla demo-v2-* no coincide con el formato generado; revise manualmente.'
    }
}
$Secretos = Initialize-DirectorioSecretos $Secretos
[void](Get-RaicesAzureDemo $Secretos)
$entorno = Get-EntornoPgDemo $Secretos $Psql
$sql = Join-Path $PSScriptRoot 'retirar.sql'
[void](Invoke-PsqlDemo $Psql $entorno '' @('-v', 'accion=comprobar', '-f', $sql))
if ($appExiste) {
    [void](Invoke-AzDemo $Az @('webapp', 'stop', '-g', 'rg-barberturno-v2-demo', '-n', 'barberturno-v2-demo-202611', '-o', 'none'))
}
try { Write-Output (Invoke-PsqlDemo $Psql $entorno '' @('-v', 'accion=retirar', '-f', $sql)) }
finally {
    [void](Invoke-PsqlDemo $Psql $entorno '' @('-v', 'accion=limpiar', '-f', (Join-Path $PSScriptRoot 'preparar-base.sql')))
}
foreach ($regla in $demoReglas) {
    [void](Invoke-AzDemo $Az @('postgres', 'flexible-server', 'firewall-rule', 'delete',
        '-g', 'rg-barberturno-prod', '-s', 'pg-barberturno-202610', '-n', $regla.name, '--yes', '-o', 'none'))
}
if ($grupoExiste) {
    [void](Invoke-AzDemo $Az @('group', 'delete', '-n', 'rg-barberturno-v2-demo', '--yes', '-o', 'none'))
}
Write-Output 'Retirada completada. Se conservan el servidor compartido, los recursos v1 y los archivos privados locales.'
