#requires -Version 7.2
<# .SYNOPSIS
Configura exclusivamente la aplicación demo existente; no publica ni crea recursos.
#>
[CmdletBinding()]
param(
    [string]$Secretos = 'D:\Proyectos\BarberTurno\.local\demo-azure', [string]$Suscripcion,
    [string]$Base = 'barberturno_v2_demo', [string]$RolMigraciones = 'bt_v2_demo',
    [string]$RolAplicacion = 'bt_v2_demo_app', [string]$TwaPaquete = '', [string]$TwaHuellas = '',
    [string]$Az = 'D:\Herramientas\azure-cli\bin\az.cmd', [switch]$Simular
)
. "$PSScriptRoot/comun.ps1"
Assert-DestinoDemo $Base $RolMigraciones $RolAplicacion
if ([bool]$TwaPaquete -ne [bool]$TwaHuellas) { throw 'Indique conjuntamente paquete y huellas TWA, o deje ambos vacíos.' }
if ($Simular) {
    Write-Output 'SIMULAR: verificar suscripción, app aislada, HTTPS y plan Linux F1; configurar prod, TLS, roles separados y health /actuator/health.'
    Write-Output 'SIMULAR: IA y WhatsApp apagados, privacidad BORRADOR demo; JSON temporal eliminado en finally; sin demo ni reloj fijo.'
    return
}
[void](Assert-AppDemo $Az $Suscripcion)
# Fallar ante residuos incompatibles sin mostrar valores ni tocar ajustes ajenos.
$existentes = (Invoke-AzDemo $Az @('webapp', 'config', 'appsettings', 'list', '-g', 'rg-barberturno-v2-demo',
    '-n', 'barberturno-v2-demo-202611', '-o', 'json')) | ConvertFrom-Json
$prohibidas = @('BT_DEMO_PASSWORD', 'BARBERTURNO_RELOJFIJO', 'BARBERTURNO_RELOJ_FIJO', 'SPRING_APPLICATION_JSON',
    'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONALLOCATION',
    'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')
if ($existentes | Where-Object { $_.name -in $prohibidas }) { throw 'Hay ajustes incompatibles con la demo prod; el coordinador debe revisarlos antes de continuar.' }
$Secretos = Initialize-DirectorioSecretos $Secretos
$ajustes = [ordered]@{
    SPRING_PROFILES_ACTIVE = 'prod'; BT_DB_URL = Get-JdbcDemo $Base
    BT_DB_USER = $RolAplicacion; BT_DB_PASSWORD = Get-SecretoDemo $Secretos 'aplicacion.secreto'
    BT_FLYWAY_USER = $RolMigraciones; BT_FLYWAY_PASSWORD = Get-SecretoDemo $Secretos 'migraciones.secreto'
    BT_JWT_SECRET = Get-SecretoDemo $Secretos 'jwt.secreto' -Crear -Jwt; BT_COOKIE_SECURE = 'true'
    BT_ADMIN_CORREO = 'plataforma-demo@ejemplo.test'; BT_ADMIN_NOMBRE = 'Administración demo'
    BT_ADMIN_PASSWORD = Get-SecretoDemo $Secretos 'admin-plataforma.secreto' -Crear
    BARBERTURNO_WHATSAPP_CONVERSACION_PRIVACIDAD_TEXTO = 'BORRADOR (demo; pendiente de P2-06): para reservar en {negocio} guardaremos tu nombre de WhatsApp y tu número. Aviso de privacidad: {enlace}. ¿Aceptas?'
    BARBERTURNO_WHATSAPP_CONVERSACION_PRIVACIDAD_ENLACE = 'https://barberturno.ejemplo.test/privacidad'
    BARBERTURNO_IA_HABILITADA = 'false'; BARBERTURNO_WHATSAPP_PROCESADOR_HABILITADO = 'false'
    BARBERTURNO_WHATSAPP_SALIDA_HABILITADO = 'false'
    SERVER_PORT = '80'; WEBSITES_PORT = '80'
    JAVA_OPTS = '-Xmx320m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k -Dfile.encoding=UTF-8'
}
if ($TwaPaquete) { $ajustes.BT_TWA_PAQUETE = $TwaPaquete; $ajustes.BT_TWA_HUELLAS = $TwaHuellas }
$temporal = Join-Path $Secretos ('appsettings-' + [guid]::NewGuid().ToString('N') + '.json')
$configuracion = Join-Path $Secretos ('siteconfig-' + [guid]::NewGuid().ToString('N') + '.json')
try {
    [IO.File]::WriteAllText($temporal, ($ajustes | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
    [void](Invoke-AzDemo $Az @('webapp', 'config', 'appsettings', 'set', '-g', 'rg-barberturno-v2-demo',
        '-n', 'barberturno-v2-demo-202611', '--settings', ('@' + $temporal), '-o', 'none'))
    [IO.File]::WriteAllText($configuracion, '{"healthCheckPath":"/actuator/health"}', [Text.UTF8Encoding]::new($false))
    # Azure CLI admite siteConfig genérico desde archivo; no existe --health-check-path en config set.
    [void](Invoke-AzDemo $Az @('webapp', 'config', 'set', '-g', 'rg-barberturno-v2-demo',
        '-n', 'barberturno-v2-demo-202611', '--generic-configurations', ('@' + $configuracion), '-o', 'none'))
} finally {
    foreach ($archivo in @($temporal, $configuracion)) { if ([IO.File]::Exists($archivo)) { [IO.File]::Delete($archivo) } }
}
Write-Output 'App Settings y health check configurados; archivo JSON temporal eliminado.'
