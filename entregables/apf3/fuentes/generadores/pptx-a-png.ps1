param([string]$Pptx, [string]$Dir)
# Abre la presentación en PowerPoint y exporta cada diapositiva a PNG (1600 px de ancho).
New-Item -ItemType Directory -Force $Dir | Out-Null
$pp = New-Object -ComObject PowerPoint.Application
try {
    $p = $pp.Presentations.Open($Pptx, $true, $false, $false)
    "diapositivas: " + $p.Slides.Count
    foreach ($s in $p.Slides) { $s.Export((Join-Path $Dir ("s{0:D2}.png" -f $s.SlideIndex)), 'PNG', 1600, 900) }
    $p.Close()
} catch { "ERROR: " + $_.Exception.Message } finally { try { $pp.Quit() } catch { } }
