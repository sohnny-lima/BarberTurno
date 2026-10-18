# CP-11 · Guía de sesiones y fichas (P1…P5)

Material para el facilitador (el responsable). Complementa [usabilidad-plantilla.md](usabilidad-plantilla.md), que conserva el guion U1…U5, los datos de partida y las reglas de registro. **CP-11 sigue en 0/5** hasta que existan cinco sesiones humanas registradas; el piloto del responsable y los E2E no cuentan.

## 1. Versión que se evalúa
- Commit final: `d2f3107` (`d2f310756321ab7d6fa7d6a8b1c7eb44f4ebd95b`) (main publicado, CI 39099141093, success). Jar: `.local/coordinacion/cp11/jar/barberturno-final.jar` (SHA-256 `aa4dcf8f282ebfe3bcf45f120d9d7fad8a7c873b05fa5b82542dfc7b25388bc2`), construido desde ese commit con `-Pcon-frontend`.
- **Ensayo del coordinador (18/10/2026 00:02, no cuenta como sesión):** base `barberturno_cp11_ensayo_s20261018_0002`, jar final con SHA-256 comprobado, perfiles `dev,demo`, reloj del servidor 28/09/2026 09:00–09:10 de Lima, jornadas de Carlos y 31 inicios libres para U1; Chromium 153.0.8010.12 a 360 px: «RELOJ COMPARTIDO OK».
- Navegador y servidor comparten el reloj fijo **28/09/2026 09:00 Lima** con `dev,demo`; el reloj de Windows **no** se cambia (el navegador lo fija Playwright en su ventana).

## 2. Distribución propuesta
| Participante | Tamaño | Orden |
|---|---|---|
| P1 | 360 px | U1 → U5 |
| P2 | Escritorio 1440 px | U1 → U5 |
| P3 | 360 px | U1 → U5 |
| P4 | Escritorio 1440 px | U1 → U5 |
| P5 | 360 px | U1 → U5 |
Tres en móvil y dos en escritorio (RNF-08 da peso al móvil). Si cambia la distribución, anótelo en la ficha.

## 3. Comandos por participante (PowerShell, desde la raíz del repositorio)
Cada orden crea una **copia nueva e independiente** `barberturno_cp11_<p>_s<fecha>` de la plantilla `barberturno_cp11_base`, arranca el jar final con `dev,demo` y el reloj fijo, comprueba reloj y horarios y abre una sola ventana. No borra ni reutiliza bases.
```powershell
powershell -ExecutionPolicy Bypass -File .local\coordinacion\cp11\sesion-cp11.ps1 -Participante P1 -Ancho 360
powershell -ExecutionPolicy Bypass -File .local\coordinacion\cp11\sesion-cp11.ps1 -Participante P2 -Ancho 1440
powershell -ExecutionPolicy Bypass -File .local\coordinacion\cp11\sesion-cp11.ps1 -Participante P3 -Ancho 360
powershell -ExecutionPolicy Bypass -File .local\coordinacion\cp11\sesion-cp11.ps1 -Participante P4 -Ancho 1440
powershell -ExecutionPolicy Bypass -File .local\coordinacion\cp11\sesion-cp11.ps1 -Participante P5 -Ancho 360
```
Antes de la persona: compruebe «RELOJ COMPARTIDO OK» en la salida, inicie usted la sesión de `cliente@ejemplo.test` (la contraseña no se anota) y deje abierta la pantalla **Reservar**. Al terminar, cierre la ventana; la siguiente orden detiene el jar anterior.

## 4. Consentimiento (leer en voz alta; registrar sí/no y fecha)
«Evaluamos la aplicación, no sus habilidades. Su participación es voluntaria; puede detenerse o retirarse en cualquier momento. Solo anotaremos un código P1…P5, tiempos, pasos, errores y comentarios sobre la interfaz. No necesitamos su nombre, correo real ni contraseña. No grabaremos audio, vídeo ni pantalla. Los resultados agregados se usarán en la sustentación académica. Puede pedir que retiremos sus datos hasta el [plazo que fije el responsable] escribiendo a [canal que fije el responsable]. ¿Acepta participar?»
Si la respuesta es no, no se inicia ni se anota nada.

## 5. Instrucciones neutrales (idénticas para todos)
- Antes de empezar: «Usará una aplicación de reservas de una barbería. Lea cada tarea, hágala como lo haría normalmente y avíseme cuando crea que terminó. Puede pensar en voz alta si quiere. No puedo ayudarle durante la tarea salvo que me lo pida; si me pide ayuda, lo anotaré.»
- Entregue cada tarea por escrito (tarjeta) con el texto exacto de la columna «Instrucción» de la plantilla. No muestre ni explique el asistente, los menús ni los botones.
- Durante la tarea: no señale la pantalla, no confirme si va bien, no use palabras de la interfaz que la persona no haya dicho. Si pide ayuda, primero «¿Qué haría usted?»; si insiste, dé la ayuda mínima y anótela.

## 6. Ficha por participante (una por persona)
```
Participante: P_   Fecha/hora: ________   Facilitador: ________
Commit: d2f3107   Navegador/versión: Chromium ______   Tamaño: 360 / 1440
Consentimiento: Sí / No (fecha ________)   Base: barberturno_cp11_p_s________

U1 Reserva principal   Inicio (s): ____  Fin (s): ____  Total: ____ s
  Entrada elegida: 01/10/2026 · Corte clásico · Carlos · franja ______
  Pasos principales (servicio / fecha-hora / confirmar / otros): ____
  Errores (acción → efecto): ______________________________
  Dificultades observadas / comentarios: ____________________
  Ayudas (cuándo y cuál): __________________________________
  Finalizó: Sí / No · Autónoma / Asistida   ≤ 180 s: Sí / No   ≤ 5 pasos: Sí / No
U2 Reprogramación   Total: ____ s  Errores: ____  Ayudas: ____  Finalizó: Sí/No (A/As)
U3 Cancelación      Total: ____ s  Errores: ____  Ayudas: ____  Finalizó: Sí/No (A/As)
U4 Política         Total: ____ s  ¿Identifica la restricción de 2 h y el mensaje?: Sí/No  Ayudas: ____
U5 Orientación      Total: ____ s  ¿Encuentra Reservar y Mis citas por el menú?: Sí/No  Ayudas: ____
Controles utilizables en este tamaño (sin desplazamiento horizontal ni controles tapados): Sí / No · notas: ____
SUS (opcional): aplicado / no aplicado · puntuación: ____
```

## 7. Después de las cinco sesiones
Copie los datos de las fichas, anonimizados, a la tabla de la plantilla y al registro de [aceptacion.md](aceptacion.md) (commit probado y resultado por participante). Se calculan: cuántos cumplen U1 ≤ 180 s y ≤ 5 pasos, finalizaciones autónomas y controles utilizables por tamaño. Cualquier incumplimiento se comunica al arquitecto; no se rellenan datos faltantes ni se sustituye a personas por pruebas automáticas.
