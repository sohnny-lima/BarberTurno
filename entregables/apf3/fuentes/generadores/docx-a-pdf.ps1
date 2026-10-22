param([string]$Docx, [string]$Pdf)
# Abre el DOCX en Word, actualiza el índice, guarda el DOCX con los números de página y exporta a PDF con marcadores.
$word = New-Object -ComObject Word.Application
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($Docx, $false, $false)
    foreach ($toc in $doc.TablesOfContents) { $toc.Update() | Out-Null }
    $doc.Repaginate()
    foreach ($toc in $doc.TablesOfContents) { $toc.UpdatePageNumbers() | Out-Null }
    $doc.Save()
    # 17 = PDF; 0 = calidad de impresión; 0 = documento completo; 1 = contenido; incluye propiedades; 1 = marcadores por títulos
    $doc.ExportAsFixedFormat($Pdf, 17, $false, 0, 0, 1, 1, 0, $true, $true, 1)
    try { $doc.Close($false) } catch { }
} catch {
    "ERROR: " + $_.Exception.Message
} finally {
    try { $word.Quit() } catch { }
}
