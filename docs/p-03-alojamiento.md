# P-03 · Alojamiento, HTTPS, monitorización y copias

Propuesta para la decisión del responsable (P-03). **No se ha contratado ni publicado nada.** Precios y condiciones consultados en fuentes oficiales el 17/10/2026; pueden cambiar.

## 1. Requisitos que condicionan la elección
- Un jar Spring Boot 4.1 / Java 21 que sirve la SPA y la API (DA-06, DA-12), perfil `prod` (rechaza reloj fijo, `demo`, cookies inseguras y variables ausentes; T-33).
- PostgreSQL **18** (DA-16) con la extensión `btree_gist` (restricción de exclusión, RNF-05) y un rol dueño sin superusuario.
- RNF-02: 99 % mensual excluido el mantenimiento ⇒ **sin suspensión por inactividad** (≤ 7,2 h de caída en 30 días).
- RNF-03: HTTPS con certificado válido; RNF-09: copia diaria, RPO 24 h, RTO 4 h; RNF-14: `/actuator/health` (200 `{"status":"UP"}`, 503 si DOWN).
- Coste mínimo para un proyecto académico; operable por una persona.

## 2. Comparación (precios en USD/mes salvo indicación)
| Opción | Coste total estimado | Límites relevantes | PostgreSQL 18 y persistencia | Suspensión por inactividad | Copias | Compatibilidad |
|---|---|---|---|---|---|---|
| **A. Azure for Students**: App Service Linux B1 + Azure Database for PostgreSQL Flexible B1MS | **0 de bolsillo**: B1 ≈ 12,41 (0,017/h) cubierto por el crédito de 100 USD (≈ 8 meses **estimados**, condicionados al consumo; ver §8); B1MS, 32 GB de almacenamiento y 32 GB de copias gratis 12 meses | Solo estudiantes a tiempo completo con correo institucional, renovación anual; B1: 1 núcleo, 1,75 GB RAM; posibles restricciones de regiones en la suscripción | 18 (GA, soporte hasta 14/11/2030), gestionado, SLA 99,9 % sin HA; `btree_gist` hay que permitirla en `azure.extensions` | No con B1 y *Always On* (F1 gratis no sirve: 1 h de CPU al día) | Automáticas con PITR, retención 7–35 días | Java SE 21 en App Service; TLS gestionado en `*.azurewebsites.net` (dominio propio opcional con certificado gratuito) |
| B. Render: Starter + Postgres Basic-256mb | ≈ 13,30 (7 + 6 + 1 GB × 0,30); con 2 GB de RAM (Standard) ≈ 31,30 | Starter: 0,5 CPU y **512 MB** (justo para Spring Boot); Hobby: 5 GB de ancho de banda | 13–18, gestionado | No en instancias de pago (la gratuita duerme a los 15 min y su BD expira a los 30 días) | PITR 3 días (Hobby) + exportaciones lógicas 7 días | Docker o nativo; TLS gestionado en `*.onrender.com` |
| C. Railway Hobby | ≈ 8–12 (5 de suscripción con 5 de uso incluido; RAM 10/GB·mes, CPU 20/vCPU·mes, volumen 0,15/GB) | Volumen 5 GB por servicio en Hobby | Imagen de PostgreSQL en contenedor con volumen (autogestionado en la plantilla) | No por defecto (*Serverless* es opcional) | Copias de volumen diarias/semanales/mensuales (6/27/89 días) | Docker; TLS en `*.up.railway.app` |
| D. VPS (DigitalOcean 2 GB) autogestionado | 12 + 3,60 de copias diarias = 15,60 (+ dominio) | 1 vCPU, 2 GB; todo el mantenimiento (parches, PostgreSQL, proxy, TLS) recae en el responsable | 18 instalado a mano | No | Instantáneas del proveedor + `tools/respaldo.sh` | Requiere Caddy/Nginx y dominio para un certificado |
| E. Oracle Cloud Always Free (A1) | 0 | Asignación reducida a 2 OCPU / 12 GB; exige tarjeta; **puede reclamar instancias inactivas** (p95 de CPU, red y memoria < 20 % durante 7 días), lo normal en una app con poco tráfico | 18 autogestionado | Riesgo de reclamación | 5 copias de volumen gratis | Igual que D |

Descartadas: niveles gratuitos que duermen (Render Free, App Service F1) por RNF-02; Hetzner CX23 (5,49 EUR sin IPv4) figura como no disponible para pedidos; bases gestionadas de pago por separado (Neon/Supabase) añaden un segundo proveedor sin ventaja.

## 3. Recomendación
**Opción A (Azure for Students)** si el responsable es elegible: coste de bolsillo 0 durante el periodo académico y la observación de RNF-02, PostgreSQL 18 gestionado con copias y PITR (cubre RNF-09 sin programar `cron`), TLS gestionado y *health check* integrado. **Alternativa B (Render, ≈ 13,30 USD/mes)** si no hay elegibilidad, condicionada a la memoria. **Es memoria real del proceso, no el tamaño del archivo** (el jar ocupa 67 MB en disco, dato que no dice nada de la RAM): el coordinador arrancó el jar de T-57 el 17/10/2026 con `-XX:MaxRAM=512m` y perfil `dev`, hizo 100 peticiones y leyó el proceso `java` en Windows: **352 MB de conjunto de trabajo (residente) y 392 MB de memoria privada** en reposo. Es una medición puntual, sin carga concurrente y fuera de un contenedor Linux; el límite de 512 MB de Render se aplica al contenedor completo, así que la cifra pertinente es la privada (392 MB) más el margen del sistema. **No acredita que quepa**: solo lo hace plausible con poco margen y habría que confirmarlo en un ensayo real antes de elegir B (0,5 CPU además alarga el arranque y el BCrypt de coste 12). Si en el ensayo supera el límite, C (Railway) antes que pagar 31 USD.

Monitorización externa (ambas): **UptimeRobot Free** (50 monitores, cada 5 min, página de estado básica) sobre `https://<host>/actuator/health` con palabra clave `"UP"`, y **Healthchecks.io Hobbyist** (20 comprobaciones) para el latido de la copia lógica semanal descrita en §5.

## 4. Lo que necesito que decida o haga el responsable
1. Elegir A, B o C (y, en A, confirmar la elegibilidad con el correo de la UTP y la región permitida).
2. Crear la cuenta y la suscripción a su nombre (yo no creo cuentas ni contrato).
3. Introducir los secretos directamente en el portal (§6), sin enviármelos.
4. Decidir si quiere un dominio propio (opcional; el subdominio del proveedor ya tiene HTTPS).

## 5. Despliegue de la opción A (pasos; los ejecuta el responsable o el coordinador con su autorización)
1. **Recursos** (portal o Azure CLI, región permitida por la suscripción): grupo `rg-barberturno`; servidor PostgreSQL Flexible **18**, Burstable **B1MS**, 32 GB, copia 7 días, redundancia local, sin HA, acceso público solo para «servicios de Azure» y una regla con la IP actual del responsable (es dinámica: actualizarla con `az postgres flexible-server firewall-rule update` antes de administrar o respaldar desde su equipo); plan App Service **Linux B1** con la app `barberturno-<sufijo>` en **Java SE 21**, *Always On* activado, *HTTPS Only* activado, TLS mínimo 1.2, FTP desactivado.
2. **Extensión:** parámetro `azure.extensions` = `BTREE_GIST` antes del primer arranque (Flyway la crea; sin esto, la migración V1 falla).
3. **Base y rol:** con el administrador del servidor y `psql` 18, las sentencias de `docs/despliegue.md` §«Base y rol de aplicación» (rol `barberturno_prod` sin superusuario, `\password` interactivo, base `barberturno_prod`).
4. **Variables (Configuración → Variables de entorno de la app; las escribe el responsable, nunca en Git ni en el chat):** `SPRING_PROFILES_ACTIVE=prod`, `BT_DB_URL=jdbc:postgresql://<servidor>.postgres.database.azure.com:5432/barberturno_prod?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`, `BT_DB_USER`, `BT_DB_PASSWORD`, `BT_JWT_SECRET` (Base64 de ≥ 32 bytes aleatorios, p. ej. `openssl rand -base64 48` en su equipo), `BT_ADMIN_CORREO`, `BT_ADMIN_NOMBRE`, `BT_ADMIN_PASSWORD`, `BT_COOKIE_SECURE=true`, `SERVER_PORT=80`, `WEBSITES_PORT=80` (enrutamiento explícito del contenedor), `JAVA_OPTS=-Xmx768m -Dfile.encoding=UTF-8`. **No** se definen `BT_DEMO_PASSWORD` ni `--barberturno.reloj-fijo` (el perfil `prod` rechaza `demo` y el reloj fijo).
5. **Artefacto:** el jar se construye desde el commit publicado y verificado en la CI (`./mvnw -Pcon-frontend clean package`), se anota su SHA-256 y se despliega con `az webapp deploy --type jar --src-path barberturno-0.0.1-SNAPSHOT.jar` (OneDeploy lo renombra a `app.jar`).
6. **Salud:** *Health check* de App Service en `/actuator/health`; comprobación manual `curl -i https://<app>.azurewebsites.net/actuator/health` → 200 `{"status":"UP"}`; cabeceras de seguridad y HSTS presentes; `http://` redirige a `https://`.
7. **Primer ingreso:** el administrador cambia la contraseña inicial. Sin cuentas demo: en producción no se cargan datos de demostración.

## 6. Copias, restauración y reversión
- **Copias (RNF-09):** automáticas del servicio con restauración a un instante (PITR), retención 7 días (ampliable hasta 35 sin coste dentro de los 32 GB gratuitos). Además, copia lógica semanal con `tools/respaldo.sh` desde el equipo del responsable (fuera del proveedor, segunda ubicación), con latido a Healthchecks.io para detectar si falta. Se lanza de madrugada (uso mínimo): el script aborta si cambian los recuentos durante la copia; si ocurre, se repite, y solo si fallara de forma reiterada se declara una ventana de mantenimiento breve con la app detenida. En el equipo del responsable, `psql`/`pg_dump` usan `PGSSLMODE=verify-full` con el certificado raíz de Azure en `%APPDATA%postgresqloot.crt` (o `require` si no se dispone de él); `sslfactory` solo aplica al driver JDBC.
- **Restauración (RTO 4 h):** restaurar a un instante crea un servidor nuevo, que nace con parámetros por defecto: antes de redirigir la app se fija y comprueba `azure.extensions = BTREE_GIST` y la regla de acceso; luego se cambia `BT_DB_URL` al servidor restaurado y se reinicia. Ensayo obligatorio antes de dar por operativo el despliegue: restaurar a «hace 1 h», generar el resumen de recuentos de `tools/respaldo-comun.sh` (función `resumir`) en el origen y en el restaurado y compararlos con `cmp` (`tools/restaurar.sh` solo sirve para volcados lógicos), cronometrar y registrar la evidencia en `docs/pruebas/`. Después se elimina el servidor sobrante: dos B1MS a la vez superan las 750 h gratuitas y consumen crédito.
- **Reversión de una versión:** conservar el jar anterior con su SHA-256; volver a desplegarlo con `az webapp deploy`. Si la versión nueva aplicó una migración Flyway, **no** se revierte el esquema: se restaura la base a un instante previo al despliegue (PITR) y se despliega el jar anterior; por eso cada despliegue con migración se anota con su hora exacta. **Consecuencia:** se pierden las reservas, cambios y registros hechos entre el despliegue y la reversión; antes de restaurar se exporta la lista de reservas creadas o modificadas en ese intervalo para reintroducirlas con la reserva asistida (RF-18) y avisar a los clientes afectados.
- **Mantenimiento:** ventanas anunciadas y registradas (inicio, fin, motivo) en el registro de operación; se excluyen del cálculo de RNF-02 solo si se registraron antes de empezar.

## 7. Evaluación de RNF-02 (99 % mensual, excluido el mantenimiento)
- **Fuente:** UptimeRobot comprueba `GET https://<host>/actuator/health` cada 5 min con palabra clave `"UP"`; solo cuenta como caída un incidente confirmado (verificación desde varias ubicaciones o dos comprobaciones consecutivas fallidas), no una muestra aislada. **Desviación documentada:** `docs/despliegue.md` pide un monitor cada minuto; el nivel gratuito ofrece 5 min. Se acepta para el proyecto académico (resolución ±5 min frente a un margen de 432 min) y se actualizará esa guía al aprobar el servicio. Exportar el registro de incidentes al cierre del periodo.
- **Periodo:** un mes natural completo tras el despliegue (o, si el calendario académico no lo permite, 30 días consecutivos, indicándolo como desviación).
- **Cálculo:** disponibilidad = 1 − (minutos caídos fuera de mantenimiento registrado) / (minutos del periodo − minutos de mantenimiento registrado). Cumple si ≥ 99 % (≤ 432 min caídos en 30 días). Resolución ±5 min por la frecuencia de comprobación.
- **Evidencia:** `docs/pruebas/rnf-02.md` con el periodo, el export del monitor, las ventanas de mantenimiento y el cálculo. **No se declara cumplido** hasta cerrar el periodo con esas pruebas.

## 8. Coste total de la configuración concreta (opción A)
Precios de lista de Azure (API pública de precios, consultada el 18/10/2026, USD, sin impuestos), región **East US / East US 2**, la más barata de las comprobadas (en Brazil South el B1 cuesta 0,02 USD/h y el B1MS 0,035 USD/h). Meses de 730 h. Condiciones de la oferta según [Azure for Students](https://azure.microsoft.com/en-us/free/students) y [Microsoft Learn](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/azurestudents-subscription-disabled).

| Componente | Configuración | Precio de lista | Cubierto por | Cargo al crédito |
|---|---|---|---|---|
| Plan App Service | Linux **B1** (1 núcleo, 1,75 GB), 1 instancia, *Always On* | 0,017 USD/h ≈ **12,41 USD/mes** | Nada (F1 gratis no sirve: 1 h de CPU al día, sin *Always On*) | **12,41 USD/mes** |
| PostgreSQL Flexible | **B1MS**, PostgreSQL 18, sin HA | 0,017 USD/h ≈ 12,41 USD/mes | 750 h/mes gratis 12 meses (un servidor: ningún mes supera 744 h) | 0 |
| Almacenamiento de la base | 32 GB | 0,115 USD/GB·mes ≈ 3,68 USD/mes | 32 GB gratis 12 meses | 0 |
| Copias de la base (PITR 7 días, LRS) | Hasta 32 GB | 0,095 USD/GB·mes | 32 GB gratis 12 meses | 0 |
| Tráfico de salida | Estimado < 5 GB/mes | 0 los primeros 100 GB/mes; después 0,087 USD/GB | Primeros 100 GB/mes | 0 |
| HTTPS | Certificado gestionado de `*.azurewebsites.net` | Incluido en el plan | — | 0 |
| *Health check* y registros en el sistema de archivos | Incluidos en B1 | Incluido | — | 0 |
| Application Insights / Log Analytics | **No se activan** (cobran por ingesta) | — | — | 0 |
| Monitor externo y latido de copias | UptimeRobot Free, Healthchecks.io Hobbyist | 0 | Fuera de Azure | 0 |
| Dominio propio | **No incluido** (opcional; registrador externo, coste anual aparte) | — | — | 0 |
| **Total** | | | | **≈ 12,41 USD/mes del crédito; 0 de bolsillo** |

**Duración del crédito: 100 / 12,41 ≈ 8 meses, como estimación condicionada al consumo.** Solo vale si se mantiene exactamente esta configuración:
- una instancia B1 y una sola base B1MS encendida (una restauración a un instante crea un segundo servidor; mientras coexistan, el segundo consume crédito, ≈ 0,41 USD/día);
- almacenamiento ≤ 32 GB y salida ≤ 100 GB/mes;
- sin Application Insights ni otros servicios;
- precios sin cambios en la región elegida (en Brazil South, la estimación baja a ≈ 6,8 meses).

El saldo real se comprueba en el [portal de patrocinios](https://www.microsoftazuresponsorships.com/balance) (Balance y Usage).

**Al agotarse el crédito o cumplirse 12 meses**, la suscripción y sus servicios se **deshabilitan**; no hay cargos automáticos porque no se registra tarjeta. Continuar exigiría pasar a pago por uso a través del soporte de Azure, con la aprobación previa del responsable. Para RNF-02, el mes de observación debe caer dentro de ese periodo.

## 9. Verificar la elegibilidad y activar Azure for Students (sin crear recursos)
**Hecho por el responsable el 18/10/2026** (activación: 100 USD, gasto 0, vencimiento 18/10/2027). La confirmación en el portal sigue en §10.

Lo ejecuta el responsable con su cuenta; el coordinador no accede a la suscripción. **No se crea ningún recurso** (ni grupo de recursos) hasta aprobar el coste de §8. No se registra tarjeta. Al informar, basta con los datos indicados; no hace falta compartir identificadores, correos ni capturas con datos personales.

1. **¿Ya existe la oferta?** Abra el [portal de patrocinios](https://www.microsoftazuresponsorships.com/balance) e inicie sesión con el correo institucional de la UTP. Si muestra un saldo, la oferta ya está activa: anote el **saldo restante** y la **fecha de expiración del crédito** y pase al paso 3.
2. **Activar:** en [Azure for Students](https://azure.microsoft.com/en-us/free/students), «Activar ahora» con el correo institucional; complete la verificación académica que se le pida y acepte los términos. Requisitos de la oferta: estudiante universitario a tiempo completo, una cuenta por persona, sin tarjeta. Si la verificación falla, **pare** y avise: se reevaluará la opción B o C (no se pasa a pago por uso).
3. **Confirmar la suscripción:** en [portal.azure.com](https://portal.azure.com) → *Suscripciones*: anote el **nombre de la oferta** (debe ser *Azure for Students*) y el **estado** (*Activa*).
4. **Regiones permitidas:** *Directiva* (Policy) → *Asignaciones*: si existe «Allowed resource deployment regions» o similar, anote las regiones permitidas. Se elegirá la más barata de §8 que esté permitida.
5. **Beneficios disponibles:** en el portal, busque *Servicios gratuitos* (Free services). Anote si aparecen «Azure Database for PostgreSQL – Flexible Server, Burstable B1MS (750 h)», «32 GB de almacenamiento» y «32 GB de copias», y su fecha de fin.

Con esos cinco datos (saldo, expiración, oferta y estado, regiones, beneficios) el coordinador recalcula §8 para la región permitida y presenta la configuración final para su aprobación antes de crear nada.

## 10. Confirmación de la suscripción en el portal (solo lectura)
**Situación del 18/10/2026:** el responsable activó Azure for Students. El resumen de activación indica 100 USD disponibles, 0 de gasto y vencimiento el 18/10/2027. Pendiente de confirmar en el portal: no se crea nada y todas las órdenes son de lectura.

1. **Saldo y vencimiento:** abra el [portal de patrocinios](https://www.microsoftazuresponsorships.com/balance) → *Balance*. Confirme: crédito 100 USD, usado 0 y fecha de expiración (bajo el gráfico) 18/10/2027.
2. **Oferta y estado:** en [portal.azure.com](https://portal.azure.com) → *Suscripciones* → su suscripción. Confirme el estado *Activa* y la oferta *Azure for Students*.
3. **Servicios gratuitos:** busque *Servicios gratuitos* (Free services) en el portal. Confirme si aparecen PostgreSQL Flexible Server B1MS (750 h), 32 GB de almacenamiento y 32 GB de copias, con su fecha de fin.
4. **Regiones y disponibilidad** con [Cloud Shell sin almacenamiento](https://learn.microsoft.com/en-us/azure/cloud-shell/get-started/ephemeral): icono `>_` de la barra superior → *Bash* → **«No storage account required»** → su suscripción → *Aplicar*. Así no se crea ninguna cuenta de almacenamiento. Pegue estas órdenes de lectura y copie la salida (no contiene secretos; puede ocultar el identificador de suscripción):
   ```bash
   az rest --method get --url "https://management.azure.com/subscriptions/$(az account show --query id -o tsv)?api-version=2022-12-01" --query "{estado:state, politicas:subscriptionPolicies}"
   az policy assignment list --query "[].{asignacion:displayName, regiones:parameters.listOfAllowedLocations.value}" -o json
   for r in eastus2 eastus southcentralus brazilsouth; do echo "== $r: $(az postgres flexible-server list-skus --location "$r" -o json 2>&1 | grep -o -i -m1 '"Standard_B1ms"' || echo 'sin B1MS o restringida')"; done
   az appservice list-locations --sku B1 --linux-workers-enabled --query "[].name" -o tsv
   az webapp list-runtimes --os linux -o tsv | grep -i "JAVA:21"
   ```
   Resultado esperado:
   - la primera orden muestra `quotaId` = `AzureForStudents_2018-01-01` y `spendingLimit` = `On`;
   - la segunda, la lista de regiones permitidas;
   - la tercera, `"Standard_B1ms"` en las regiones donde la suscripción puede crear el B1MS («sin B1MS o restringida» descarta la región);
   - la cuarta, las regiones con App Service B1 en Linux;
   - la quinta, que existe Java 21 para App Service Linux.

   La comprobación definitiva de la región se hará al crear los recursos: el asistente valida la región antes de confirmar.

**Regla de elección:** la región más barata de §8 que esté permitida y ofrezca B1MS. En orden: East US 2, East US, South Central US y, por último, Brazil South (≈ 6,8 meses de crédito).

## 11. Configuración final propuesta (pendiente de la región y de la aprobación del responsable)
Se mantiene la arquitectura documentada (DA-06, DA-12): **un único jar de Spring Boot con la SPA de Angular dentro**, en **App Service Linux (Java SE 21)**, y **PostgreSQL 18 administrado**. Sin frontend separado ni Container Apps (no hay una necesidad demostrada).

| # | Recurso | Nombre propuesto | Configuración | Coste |
|---|---|---|---|---|
| 1 | Grupo de recursos | `rg-barberturno-prod` | Región elegida (§10) | 0 |
| 2 | Azure Database for PostgreSQL Flexible | `pg-barberturno-<sufijo>` | PostgreSQL **18**, Burstable **B1MS**, 32 GB Premium SSD **sin crecimiento automático** (para no salir de los 32 GB gratuitos), copias 7 días LRS, sin HA, TLS obligatorio, acceso público solo para servicios de Azure + la IP del responsable, `azure.extensions = BTREE_GIST`, base y rol `barberturno_prod` (docs/despliegue.md) | 0 (gratuito 12 meses) |
| 3 | Plan de App Service | `asp-barberturno` | Linux **B1**, 1 instancia, sin escalado automático | **0,017 USD/h ≈ 12,41 USD/mes** |
| 4 | Aplicación web | `barberturno-<sufijo>` | Java SE 21, *Always On*, *HTTPS Only*, TLS ≥ 1.2, FTP desactivado, *Health check* `/actuator/health`, registros en el sistema de archivos, variables de §5 (las introduce el responsable) | Incluido en el plan |
| 5 | Grupo de acciones (correo) | `ag-barberturno` | Correo del responsable | 0 (notificaciones por correo incluidas) |
| 6 | Alertas del registro de actividad | `al-barberturno-cambios` | Aviso por correo ante cualquier creación, modificación o borrado de recursos en la suscripción y ante *Service Health* | 0 |
| 7 | Bloqueo de recurso | `CanNotDelete` sobre el servidor PostgreSQL | Evita borrados accidentales de la base | 0 |
| — | Externos | UptimeRobot Free y Healthchecks.io Hobbyist | Monitor de `/actuator/health` cada 5 min y latido de la copia semanal | 0 |

**No se crean:** Application Insights ni Log Analytics (cobran por ingesta), dominio propio, ranuras de despliegue (requieren el nivel Standard), segunda base ni Container Registry.

### Consumo estimado del crédito (estimación condicionada al consumo)
| Concepto | Valor |
|---|---|
| Cargo mensual previsto | ≈ 12,41 USD (solo el plan B1; el resto es gratuito o 0) |
| Cargo semanal de referencia | ≈ 2,86 USD |
| Crédito | 100 USD, vence el 18/10/2027 |
| Duración estimada | ≈ 8 meses desde la creación del plan (p. ej., creado el 20/10/2026 → agotamiento previsto hacia mediados de junio de 2027), **antes** del vencimiento. Supone una sola instancia B1, una sola base encendida, ≤ 32 GB, ≤ 100 GB de salida al mes y precios sin cambios |
| Periodo de RNF-02 | Un mes natural completo dentro de ese plazo (p. ej., noviembre de 2026) |
| Ahorro opcional tras la evaluación académica | Pasar el plan a F1 (gratis) o eliminarlo detiene el cargo de 12,41 USD/mes; la base sigue siendo gratuita hasta el 18/10/2027. Requiere decisión del responsable (F1 no tiene *Always On*: deja de servir para RNF-02) |

### Alertas de gasto: lo que admite esta oferta
- **Presupuestos de Cost Management: no disponibles.** Microsoft Learn lista *Azure for Students (MS-AZR-0170P)* entre las ofertas **no admitidas** por Cost Management ([fuente](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/understand-cost-mgt-data)), así que no se pueden crear presupuestos con alertas de importe. Si el portal ofreciera *Presupuestos* en la suscripción, se crearía uno de 15 USD/mes con avisos al 50, 80 y 100 %. No se cuenta con ello.
- **Sustitutos que sí funcionan:**
  1. Alerta del registro de actividad (recurso 6): cualquier recurso nuevo o modificado, la principal causa de un gasto inesperado, avisa por correo en minutos.
  2. **Revisión semanal del portal de patrocinios** (*Balance* y *Usage*), cotejada con la referencia de ≈ 2,86 USD/semana. Si el gasto acumulado supera la curva prevista en más de un 20 %, se investiga en *Usage* qué servicio lo consume. El portal se actualiza con 24–72 h de retraso.
  3. Aviso por correo de Azure al alcanzar el límite de gasto.
  4. Opcional: un recordatorio semanal en el calendario del responsable (se crea solo si él lo pide).

### Qué protege el límite de gasto
- La suscripción tiene un **límite de gasto igual al crédito (100 USD)**: no se puede subir ni personalizar, y quitarlo exige registrar un medio de pago y pasar a pago por uso ([límite de gasto](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/spending-limit); [suscripción deshabilitada](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/azurestudents-subscription-disabled)). Sin tarjeta registrada **no puede haber cargos al responsable**.
- **Qué ocurre al alcanzarlo o al vencer el 18/10/2027:** Azure deshabilita la suscripción y sus servicios. La aplicación deja de responder, los recursos salen de producción y los datos de almacenamiento quedan en solo lectura. Para seguir habría que pasar a pago por uso (decisión del responsable).
- **Qué no protege:**
  - la **disponibilidad**: agotar el crédito es una caída total (afectaría a RNF-02), de ahí la revisión semanal;
  - el **consumo indebido del crédito** por recursos olvidados (p. ej., un segundo servidor tras una restauración);
  - los servicios de Marketplace de terceros que se facturan aparte (no se usan; sin medio de pago no se pueden contratar).

### Lo que se aprueba con esta configuración
Región (la que resulte de §10), los recursos 1–7 y el cargo previsto de ≈ 12,41 USD/mes contra el crédito (0 de bolsillo). Tras la aprobación, por este orden: creación de recursos, secretos introducidos por el responsable, despliegue del jar verificado en la CI, HTTPS, *Health check*, monitor externo, ensayo de restauración y comienzo del periodo de RNF-02.
