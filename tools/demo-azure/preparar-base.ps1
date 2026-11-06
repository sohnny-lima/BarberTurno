#requires -Version 7.2
<# .SYNOPSIS
Prepara exclusivamente los roles y la base demo, con TLS y SCRAM calculado en el equipo.
#>
[CmdletBinding()]
param(
    [string]$Secretos = 'D:\Proyectos\BarberTurno\.local\demo-azure',
    [string]$Base = 'barberturno_v2_demo', [string]$RolMigraciones = 'bt_v2_demo',
    [string]$RolAplicacion = 'bt_v2_demo_app',
    [string]$Psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe', [switch]$Simular
)
. "$PSScriptRoot/comun.ps1"
Assert-DestinoDemo $Base $RolMigraciones $RolAplicacion
if ($Simular) {
    Write-Output 'SIMULAR: validar PostgreSQL 18/btadmin, atributos, membresías y propietarios; preparar solo barberturno_v2_demo y sus dos roles.'
    Write-Output 'SIMULAR: verificar las dos raíces SHA-256; usar SCRAM; conceder SET temporal; retirarlo en finally y verificar ACL.'
    return
}
$Secretos = Initialize-DirectorioSecretos $Secretos
[void](Get-RaicesAzureDemo $Secretos -Descargar)
$entorno = Get-EntornoPgDemo $Secretos
$version = Invoke-ProcesoDemo $Psql @('--version')
if ($version -notmatch ' 18\.') { throw 'Se requiere psql 18.' }
[void](Invoke-PsqlDemo $Psql $entorno '' @('-f', (Join-Path $PSScriptRoot 'validar-base.sql')))
$propietario = Get-SecretoDemo $Secretos 'migraciones.secreto' -Crear
$aplicacion = Get-SecretoDemo $Secretos 'aplicacion.secreto' -Crear
if ($propietario -ceq $aplicacion -or $entorno.PGPASSWORD -ceq $propietario -or $entorno.PGPASSWORD -ceq $aplicacion) {
    throw 'Las tres contraseñas de base de datos deben ser diferentes.'
}
$entorno.BT_DEMO_SCRAM_PROPIETARIO = Get-VerificadorScram $propietario
$entorno.BT_DEMO_SCRAM_APP = Get-VerificadorScram $aplicacion
$sql = Join-Path $PSScriptRoot 'preparar-base.sql'
try { [void](Invoke-PsqlDemo $Psql $entorno '' @('-v', 'accion=preparar', '-f', $sql)) }
finally {
    $entorno.Remove('BT_DEMO_SCRAM_PROPIETARIO'); $entorno.Remove('BT_DEMO_SCRAM_APP')
    [void](Invoke-PsqlDemo $Psql $entorno '' @('-v', 'accion=limpiar', '-f', $sql))
}
Write-Output (Invoke-PsqlDemo $Psql $entorno '' @('-v', 'accion=informe', '-f', $sql))
Write-Output 'Base preparada. SET temporal retirado; ADMIN automático del creador conservado sin SET ni INHERIT.'
