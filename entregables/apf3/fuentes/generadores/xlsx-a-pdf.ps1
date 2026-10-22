param([string]$Xlsx, [string]$Pdf)
# Abre el libro en Excel en solo lectura y exporta la hoja activa a PDF (sin modificar el archivo).
$xl = New-Object -ComObject Excel.Application
$xl.Visible = $false
$xl.DisplayAlerts = $false
try {
    $wb = $xl.Workbooks.Open($Xlsx, 0, $true)
    $hoja = $wb.Worksheets.Item(1)
    "hoja: " + $hoja.Name + " · filas usadas: " + $hoja.UsedRange.Rows.Count + " · fórmulas: " + $(try { $hoja.UsedRange.SpecialCells(-4123).Count } catch { 0 })
    $hoja.PageSetup.PrintGridlines = $true
    $hoja.PageSetup.PrintHeadings = $true
    $hoja.PageSetup.Zoom = $false
    $hoja.PageSetup.FitToPagesWide = 1
    $hoja.PageSetup.FitToPagesTall = 1
    $hoja.PageSetup.Orientation = 2
    $hoja.ExportAsFixedFormat(0, $Pdf)
    $wb.Close($false)
} catch { "ERROR: " + $_.Exception.Message } finally { try { $xl.Quit() } catch { } }
