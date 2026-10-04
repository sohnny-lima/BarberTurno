# Extrae solo agregados del HTML Gatling y de manifiestos sin credenciales.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Ejecucion,
    [Parameter(Mandatory)][string]$Informe,
    [Parameter(Mandatory)][string]$Salida
)
$ErrorActionPreference = 'Stop'
$invariante = [Globalization.CultureInfo]::InvariantCulture
$entorno = [IO.File]::ReadAllText((Join-Path $Ejecucion 'entorno.json')) | ConvertFrom-Json
$html = [IO.File]::ReadAllText((Join-Path $Informe 'index.html'))
$campos = @('solicitudes','ok','ko','erroresPorcentaje','peticionesSegundo','minMs',
    'p50Ms','p75Ms','p95Ms','p99Ms','maxMs','mediaMs','desviacionMs')
$peticiones = @()
foreach ($fila in [regex]::Matches($html, '<tr id="(?:ROOT|req_[^"]+)"[^>]*>(.*?)</tr>', 'Singleline')) {
    $contenido = $fila.Groups[1].Value
    $nombre = [regex]::Match($contenido, 'class="ellipsed-name">([^<]+)</span>').Groups[1].Value
    $datos = [ordered]@{nombre = [Net.WebUtility]::HtmlDecode($nombre)}
    $valores = [regex]::Matches($contenido, '<td class="value [^"]* col-(\d+)">([^<]+)</td>')
    if ($valores.Count -ne 13) { throw 'La tabla Gatling cambió; revise el extractor antes de publicar el resumen.' }
    foreach ($valor in $valores) {
        $indice = [int]$valor.Groups[1].Value - 2
        $datos[$campos[$indice]] = [double]::Parse($valor.Groups[2].Value, $invariante)
    }
    $peticiones += $datos
}
if ($peticiones.Count -ne 6) { throw 'Se esperaban seis filas: global y cinco peticiones HTTP.' }
$negocio = [ordered]@{}
foreach ($linea in [IO.File]::ReadAllLines((Join-Path $Ejecucion 'resultado-negocio.properties'))) {
    if ($linea -match '^(creadas|conflictosEsperados|limitesEsperados|recorridosConsulta|recorridosCrear|sinFranja|rechazosInesperados)=(\d+)$') {
        $negocio[$Matches[1]] = [int]$Matches[2]
    }
}
$rechazos = @()
$archivoRechazos = Join-Path $Ejecucion 'rechazos-inesperados.tsv'
if (Test-Path -LiteralPath $archivoRechazos) {
    foreach ($fila in @(Import-Csv -LiteralPath $archivoRechazos -Delimiter "`t")) {
        $rechazos += [ordered]@{estado = [int]$fila.estado; codigo = [Uri]::UnescapeDataString($fila.codigo); cantidad = [long]$fila.cantidad}
    }
    $totalRechazos = ($rechazos | Measure-Object -Property cantidad -Sum).Sum
    if ([long]$totalRechazos -ne $negocio.rechazosInesperados) { throw 'El desglose de rechazos no coincide con su total.' }
} elseif ($negocio.Contains('rechazosInesperados')) {
    throw 'Falta el desglose de rechazos de esta ejecución.'
}
$log = [IO.File]::ReadAllText((Join-Path $Ejecucion 'gatling.log'))
if (-not $log.Contains('BUILD SUCCESS')) { throw 'Solo se resumen ejecuciones Gatling terminadas correctamente.' }
$resumen = [ordered]@{
    entorno = $entorno
    informeLocal = Split-Path $Informe -Leaf
    peticiones = $peticiones
    negocio = $negocio
    rechazosInesperados = $rechazos
    asercionesGatling = 'BUILD SUCCESS; umbrales originales cumplidos'
    limpieza = [IO.File]::ReadAllText((Join-Path $Ejecucion 'limpieza.log')).Trim()
}
$destino = [IO.Path]::GetFullPath($Salida)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destino)) | Out-Null
[IO.File]::WriteAllText($destino, ($resumen | ConvertTo-Json -Depth 6).Replace("`r`n", "`n") + "`n", [Text.UTF8Encoding]::new($false))
Write-Output "Resumen agregado: $Salida"
