# Muestra Javadoc de BarberTurno

Muestra académica de tres clases reales del backend, preparada el 01/10/2026.
Las fuentes corresponden al commit **d03544c7e7d9c01618a97ccde9f770af6b21006b**;
la configuración de generación está en backend/pom.xml y el verificador en tools/verificar-enlaces-html.mjs.
Las tres fuentes son idénticas en `main` tras el cierre de T-40 (comprobado en la revisión, commit `3170962`).
La muestra se genera con los mismos controles estrictos de Javadoc que la API completa (`doclint=all`, `failOnWarnings=true`).

- `ReglasTemporales`: evolución del anexo ReservaService del APF2, con RN-01 y los límites RN-07/08.
- `CalculadoraFranjas`: núcleo de RF-07; jornada, rejilla, horizonte y solapes de RN-05.
- `PoliticaTransiciones`: estados, actores y ventanas de RN-10, RN-11 y RN-12; delega cancelación en RN-07/08.

`fuentes/` conserva exactamente los bytes de los tres archivos del commit de origen,
con la ruta `pe/barberturno/<paquete>/<clase>.java`. Los tipos anidados aparecen también en
`html/`; no son clases adicionales elegidas para la muestra. El material congelado `docs/apf2/` permanece intacto.

Para regenerar, use JDK 21 y las dependencias previamente descargadas. Desde `backend/`:

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
.\mvnw.cmd -o -P javadoc-muestra javadoc:javadoc
Set-Location ..
node tools/verificar-enlaces-html.mjs entregables/apf2-final/javadoc/html
~~~

Abra [html/apidocs/index.html](html/apidocs/index.html) para explorar las clases, el autor y sus versiones.
El comando funciona sin red; el HTML no enlaza a documentación externa.
El plugin 3.12.0 agrega apidocs al directorio compartido outputDirectory; por eso el índice
está en html/apidocs/index.html. La muestra omite páginas de uso (use=false), que incluirían
referencias a clases del backend no seleccionadas, y conserva doclint=all y failOnWarnings=true.
La generación incluye las tres unidades fuente y sus tipos anidados, no el resto del backend.
El perfil solo escribe HTML; para actualizar las copias exactas desde la raíz:

~~~powershell
$clases = @('reservations/ReglasTemporales.java', 'scheduling/CalculadoraFranjas.java', 'reservations/PoliticaTransiciones.java')
foreach ($clase in $clases) {
    $destino = Join-Path 'entregables/apf2-final/javadoc/fuentes/pe/barberturno' $clase
    New-Item -ItemType Directory -Force (Split-Path $destino) | Out-Null
    Copy-Item -LiteralPath (Join-Path 'backend/src/main/java/pe/barberturno' $clase) -Destination $destino
}
~~~

Registre el nuevo commit de origen al actualizar las fuentes y repita la comparación de bytes.
Los encabezados de generación de Javadoc incluyen su fecha; reproducible significa aquí
mismo comando, selección, configuración y estructura, no identidad entre fechas diferentes.

### Normalizar el texto generado antes de versionar

El doclet de JDK 21 usa CRLF en Windows y deja algunos espacios sobrantes.
Desde backend/, después de la generación, normalice exclusivamente la salida de la muestra:

~~~powershell
$utf8 = [System.Text.UTF8Encoding]::new($false)
Get-ChildItem ../entregables/apf2-final/javadoc/html -Recurse -File |
    Where-Object Extension -ne '.png' | ForEach-Object {
        $texto = [IO.File]::ReadAllText($_.FullName).Replace("`r`n", "`n")
        $texto = [regex]::Replace($texto, '(?m)[ \t]+$', '')
        [IO.File]::WriteAllText($_.FullName, $texto, $utf8)
    }
~~~

Este paso conserva los PNG y solo ajusta espacios y finales de línea de los archivos textuales.
