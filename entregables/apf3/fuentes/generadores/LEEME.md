# Generadores del APF3

Scripts con los que se produjeron el informe, la presentación y las evidencias de esta entrega. Se ejecutaron el 21/10/2026 en el equipo del responsable (Windows 11, Node 20, Microsoft Office) y contienen rutas absolutas de ese equipo: para reutilizarlos hay que ajustar las rutas.

| Archivo | Uso |
|---|---|
| `lib-docx.js`, `informe.js` | Informe DOCX con la biblioteca `docx` 9 |
| `t58-datos.js` → `t58.json` | Datos verificados de T-58 que consumen el informe y la presentación |
| `presentacion.js` | Presentación con `pptxgenjs` 3 |
| `docx-a-pdf.ps1`, `pptx-a-png.ps1`, `xlsx-a-pdf.ps1` | Word, PowerPoint y Excel por COM: índice, PDF y vistas previas |
| `capturas-github*.mjs`, `capturas-azure.mjs`, `salud.mjs` | Capturas de páginas públicas con Playwright (solo GET, sin sesión) |
| `render-svg.mjs` | Diagramas SVG → PNG |
| `pdf-a-png.mjs`, `contactos.mjs`, `paginas-secciones.mjs`, `recorte.mjs` | Revisión visual del PDF y utilidades |
| `rellenar.js`, `*.plantilla.md` | Rellenan la matriz y el README con páginas y estado |

Dependencias (no incluidas): `docx@9`, `pptxgenjs@3`, `pdfjs-dist@4.10.38` y el Playwright del frontend del repositorio.
