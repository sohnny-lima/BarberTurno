# Capturas de T-57 · Fase 2 verde

Las subcarpetas [chromium](chromium/) y [firefox](firefox/) contienen la misma matriz: **49 PNG por navegador, 98 en total**. Cada `auditoria.json` registra el proyecto, la versión del navegador, el tamaño del viewport y del PNG, axe, ausencia de desplazamiento horizontal, contornos outline y foco de teclado de 3 px verde con desfase de 2 px.

Cada nombre de la tabla se completa con el ancho y `.png`, por ejemplo `Ingresar360.png`. Las páginas completas amplían la altura para mostrar todo el contenido; los diálogos conservan el viewport original (360 × 800 o 1440 × 900). Todos los datos son ficticios. Se emplea el backend real salvo las respuestas expresamente señaladas como simuladas.

| Nombre base | Anchos | Qué revisar |
|---|---|---|
| Ingresar | 360, 1440 | Formulario, acción principal y foto decorativa de escritorio. |
| Registro | 360, 1440 | Ayuda de contraseña, casilla/enlace de privacidad y foto. |
| Privacidad | 360, 1440 | Medida de lectura y enlace de regreso. |
| MiCuenta | 360, 1440 | Datos personales, Seguridad y título único. |
| PasswordVoluntario | 360, 1440 | Cambio de contraseña sin aviso de obligación. |
| PasswordObligatorio | 360, 1440 | Sesión simulada con debeCambiarPassword; aviso, título visible y Salir. |
| Servicios | 360, 1024, 1440 | Una/dos columnas de tarjetas y tabla de escritorio; mismas acciones. |
| DialogoServicio | 360, 1440 | Campos outline, ayudas y pie visible; Nombre con un ejemplo ficticio sin guardar. |
| Barberos | 360, 1440 | Tarjetas/tabla con datos y acciones. |
| DialogoBarbero | 360, 1440 | Tipo de alta completo, Generar y nota de entrega. |
| DialogoBarberoNota | 360 | Contenido desplazado hasta la nota de entrega completa, con pie y foco visibles; complementa la vista inicial del mismo diálogo. |
| DialogoPasswordTemporal | 360, 1440 | Alta real de una cuenta ficticia efímera; credencial de demostración, aviso, Copiar y único Cerrar. Escape no lo cierra. |
| DialogoRiesgo | 360, 1440 | Desactivar Carlos con botón de riesgo; se cancela sin cambiar el estado. |
| HorariosSinBarbero | 360, 1024, 1440 | Selector y orientación sin selección. |
| HorariosCarlos | 360, 1024, 1440 | Semana, Bloqueos y Nuevo bloqueo; fecha completa a 16 px. También se comprueba a 1200, 1280 y 1360 px sin generar PNG adicionales. |
| Reportes | 360, 1440 | Total a todo el ancho + estados 2 × 3 en móvil; Total a la izquierda de estados 3 × 2 en escritorio; barras e historial reales. También se comprueba a 768 px. |
| Usuarios | 360, 1024, 1440 | Filtros sin hueco inferior, acciones del mismo ancho a 360, tarjetas intermedias y tabla. También se comprueban acciones a 768 y 1199 px. |
| BarberosCarga | 360, 1440 | Petición retenida: esqueleto y anuncio de carga. |
| BarberosVacio | 360, 1440 | Lista simulada vacía y acción existente. |
| BarberosError | 360, 1440 | Respuesta simulada 503: alerta y Reintentar. |
| ReportesCarga | 360, 1440 | Resumen retenido: estado de carga. |
| ReportesVacio | 360, 1440 | Resumen e historial simulados a cero: seis estados, conciliación y vacíos. |
| ReportesError | 360, 1440 | Respuesta simulada 503: alerta y Reintentar. |

Revise primero 360 px para legibilidad, acciones y diálogos; después 1024 para las dos columnas y 1440 para tablas y distribución de cifras. El control elegido en cada captura pasa por una comprobación de foco visible antes de ejecutar el helper común de auditoría. Las capturas previas de T-55 permanecen en su carpeta original.
