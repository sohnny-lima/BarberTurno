// Actualiza docs/cierre.md con el punto final del APF3. Uso: CI_ID=... CI_RES=... node cierre-apf3.js
const fs = require('fs');
const P = 'D:/Proyectos/BarberTurno/docs/cierre.md';
let s = fs.readFileSync(P, 'utf8');
const CI = `${process.env.CI_ID} (${process.env.CI_RES})`;
const rep = (a, b) => { if (!s.includes(a)) throw new Error('ancla: ' + a.slice(0, 60)); s = s.replace(a, b); };

rep('Última actualización: **19/10/2026**. Freeze vigente (AGENTS.md §5).',
  'Última actualización: **21/10/2026** (punto final del APF3). Freeze vigente (AGENTS.md §5).');

rep('## 1. Terminado y acreditado', `## 0. Punto final del APF3 (21/10/2026)
| Elemento | Valor |
|---|---|
| **Commit del código entregado** | \`3bcd5d0\` en \`main\`: merge \`--no-ff\` de T-58 (exportación a Excel con POI, Commons Lang, Guava y Logback; MJ-22, DA-29). Árbol idéntico a \`e0ebc54\`, revisado por el coordinador y por Gemini (g037, APROBADO). Anotación del merge: \`12a4185\` (solo documentación) |
| **CI del código** | Ejecución **${CI}** sobre \`12a4185\` (código de \`3bcd5d0\` + documentación) |
| **Commit que publica la documentación** | El commit siguiente de \`main\` («docs: publicar los materiales del APF3…»): \`entregables/apf3/\` sin los ZIP, \`entregables/apf2-final/APF2_BarberTurno_Informe_Final.pdf\` (SHA-256 \`ae9bce90…65cc9f\`, sin modificar; distinto del borrador \`docs/apf2/\`), \`docs/continuidad-v2.md\` y la medición de Java del 21/10. Su hash se anota en §0.1 |
| **Archivos para el aula** | \`entregables/apf3/APF3_BarberTurno_Entrega.zip\` (solo local, no versionado): informe PDF/DOCX, presentación, guion, matriz, README, evidencias, fuentes y \`codigo/BarberTurno-3bcd5d0.zip\` (\`git archive\`) |
| **Versión desplegada** | Azure ejecuta **\`d2f3107\`** (19/10/2026), sin T-58. No se redespliega por decisión del responsable: en producción no aparece «Descargar Excel» y la demostración de T-58 se hace en local |
| **Java (RA-02, informativa)** | 28,78 % del producto y 41,56 % con pruebas (\`medicion-java.md\`, \`12a4185\`) frente al 60 % estimado en el APF2. Explicado en el informe del APF3 §11.1; sin cambiar la métrica |
| **Pendientes** | CP-11 (0/5), RNF-02 (noviembre), PM-1…PM-8, restauración en Azure, protecciones operativas, RNF-07 y validación con el negocio (§2) |
| **Continuidad** | \`docs/continuidad-v2.md\` |

## 1. Terminado y acreditado`);

rep('| **Publicación del código** | `origin/main`: código en `d2f3107` | CI **39099141093** sobre `d2f3107`: **success**. Los commits posteriores son solo documentación |',
  '| **Publicación del código** | `origin/main`: código desplegado en `d2f3107`; código del APF3 en `3bcd5d0` (T-58) | CI **39099141093** sobre `d2f3107`: **success**. CI **' + CI + '** sobre `12a4185` (código de `3bcd5d0`). Los commits posteriores son solo documentación |\n| **T-58 · exportación a Excel** | `docs/pruebas/t-58.md`, `docs/tareas.md` | TDD en tres ciclos; verify 1578/1578, Vitest 450/450 (Lima y Madrid), build 496,42 kB, E2E 48/48; Gemini g037 APROBADO; integrada en `main` (`3bcd5d0`). **No desplegada** |');

rep('\n\n## 4. Mejoras futuras fuera del cierre', `
| Redespliegue de T-58 | Decidir si Azure pasa de \`d2f3107\` a la versión entregada (\`3bcd5d0\`); hasta entonces, la exportación a Excel solo se demuestra en local. |

## 4. Mejoras futuras fuera del cierre
- Propuestas para v2 (multitenencia, roles de propietario o plataforma, WhatsApp e IA): ver \`docs/continuidad-v2.md\`. No están implementadas ni comprometidas.`);
fs.writeFileSync(P, s);
console.log('ok');
