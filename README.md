# LastMile Platform

Plataforma de logística de última milla (inspirada en recibelo.cl / foxtrot.io)
cuyo diferenciador es un motor que **aprende, por cliente, la ventana horaria
en que suele recibir vs. rechazar** un envío, y usa ese aprendizaje para armar
el orden óptimo de entrega de la ruta del camión.

## Arquitectura (microservicios)

```
                         ┌─────────────────────┐
                         │   Frontend (React)  │
                         │  Vite + TS + Tailwind│
                         └──────────┬───────────┘
                                    │ HTTPS
                         ┌──────────▼───────────┐
                         │     API Gateway       │  Spring Cloud Gateway
                         │  valida JWT (borde)   │  puerto 8080
                         └───┬───────┬───────┬───┘
                 ┌───────────┘       │       └────────────┐
        ┌────────▼───────┐  ┌────────▼────────┐  ┌────────▼─────────┐
        │  auth-service   │  │ delivery-service │  │  import-service   │
        │  JWT + bcrypt   │  │ ventanas horarias│  │  Node/TS + Excel  │
        │  Spring Boot    │  │ + ruteo (Java)   │  │  /CSV -> shipments│
        │  puerto 8081    │  │ puerto 8082      │  │  puerto 8083      │
        └────────┬────────┘  └────────┬─────────┘  └───────────────────┘
                  │                    │
             ┌────▼────┐          ┌────▼────┐
             │ authdb  │          │deliverydb│   PostgreSQL
             └─────────┘          └──────────┘
```

## Estructura del repositorio

```
.
├── services/                 # Microservicios backend
│   ├── api-gateway/          # Spring Cloud Gateway
│   ├── auth-service/         # Spring Boot + Spring Security
│   ├── delivery-service/     # Spring Boot + PostgreSQL
│   └── import-service/       # Node 20 + TypeScript + Express
├── frontend/                 # React + Vite + TypeScript
├── infra/
│   ├── postgres/             # Scripts de inicialización de PostgreSQL
│   └── seed/                 # Datos semilla para desarrollo y demo
├── docs/
│   ├── adr/                  # Architecture Decision Records
│   ├── api/                  # Documentación de APIs
│   └── plan/                 # Plan de trabajo, sprints y hallazgos
├── CLAUDE.md                 # Reglas del proyecto para sesiones de Claude Code / agentes
├── docker-compose.yml
└── docker-compose.override.yml  # Solo desarrollo: activa el perfil seed
```

Las decisiones de arquitectura se registran en [`docs/adr/`](docs/adr/). Las reglas
de trabajo (ramas, commits, PRs, issues) están en [`CLAUDE.md`](CLAUDE.md).

## Servicios

| Servicio | Stack | Responsabilidad |
|---|---|---|
| `api-gateway` | Java, Spring Cloud Gateway | Punto único de entrada, valida JWT una sola vez y enruta a cada microservicio, propaga `X-User-Id` / `X-User-Role` (descarta los que manda el cliente) |
| `auth-service` | Java, Spring Boot, Spring Security | Registro/login, hash de contraseñas con **bcrypt**, emisión y refresh de **JWT** |
| `delivery-service` | Java, Spring Boot, PostgreSQL | Clientes, envíos, registro de intentos de entrega; **`TimeWindowLearningService`** (aprendizaje de ventana horaria por cliente) y **`RouteOptimizationService`** (orden de entrega: ventana horaria + cercanía geográfica) |
| `import-service` | Node.js, TypeScript, Express | Recibe planillas **.xlsx / .csv** (ExcelJS + PapaParse), las valida y crea los envíos en `delivery-service` |
| `frontend` | React, Vite, TypeScript, Tailwind, Zustand | Login, carga de planillas, panel de ruta del día con la ventana horaria estimada por parada |

## Cómo funciona el aprendizaje de ventanas horarias

1. Cada vez que el camión llega donde un cliente, se registra un
   `DeliveryAttempt` con la hora exacta y el resultado (`ACCEPTED`,
   `REJECTED_NOT_HOME`, etc.) vía `POST /api/shipments/attempts`.
2. `TimeWindowLearningService` arma, por cliente, un histograma de 24 horas:
   cada aceptación suma puntaje a esa hora, cada rechazo resta (con más peso,
   para evitar esas franjas a futuro).
3. Cuando se pide `POST /api/routes/build` para una fecha, `RouteOptimizationService`:
   - obtiene el perfil de ventana horaria aprendido de cada cliente con envío ese día,
   - ordena primero por franja horaria recomendada (y confianza del histórico),
   - dentro de una misma franja, aplica *nearest-neighbor* geográfico (fórmula
     de Haversine) para minimizar el recorrido del camión,
   - persiste `routePosition` en cada `Shipment` y devuelve la secuencia final.

## Cómo levantar todo con Docker

```bash
cp .env.example .env
# edita .env y reemplaza los valores reemplaza_...:
#   JWT_SECRET               clave real de al menos 32 caracteres
#   POSTGRES_PASSWORD        contraseña de PostgreSQL (y del datasource)
#   REDIS_PASSWORD           contraseña de Redis
#   BOOTSTRAP_ADMIN_EMAIL    email del primer ADMIN
#   BOOTSTRAP_ADMIN_PASSWORD contraseña del primer ADMIN (mín. 8 caracteres)

docker compose up --build
```

| Servicio | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API Gateway | http://localhost:8080 |
| auth-service (directo) | http://localhost:8081 |
| delivery-service (directo) | http://localhost:8082 |
| import-service (directo) | http://localhost:8083 |
| PostgreSQL | localhost:5432 |

### Healthchecks y datos semilla

`docker compose up` espera a que cada dependencia esté **healthy** antes de
arrancar el siguiente servicio (Postgres/Redis → auth y delivery → import →
gateway → frontend). Para ver el estado: `docker compose ps`.

`docker-compose.yml` **no** activa datos de ejemplo. En desarrollo, el
`docker-compose.override.yml` versionado (que `docker compose` aplica solo
cuando corres `docker compose up` en la raíz) activa el perfil **`seed`**, que
carga datos de ejemplo de forma idempotente (detalle en [`infra/seed/`](infra/seed/README.md)):

- un usuario por rol (`admin@`, `despacho@`, `conductor@`, `operador@` y `cliente@lastmile.cl`), todos con la contraseña `lastmile123`;
- 30 locales comerciales de Santiago (botillerías, minimarkets, bares y restaurantes) con tipo, modulación declarada y 60 días de historial de intentos; 11 declaran un horario distinto de su ventana real;
- 50 envíos para la fecha de hoy.

| Quiero… | Comando |
|---|---|
| Desarrollo con datos semilla | `docker compose up --build` (usa el override) |
| Desarrollo sin datos semilla | `SPRING_PROFILES_ACTIVE=default docker compose up --build` |
| Como en staging/producción (sin override) | `docker compose -f docker-compose.yml up --build` |

> Las contraseñas semilla son públicas. Fuera de tu máquina usa siempre
> `-f docker-compose.yml` (o un override propio), nunca el de desarrollo.

## Flujo típico de uso (vía gateway, puerto 8080)

```bash
# 1. Login como el ADMIN inicial (BOOTSTRAP_ADMIN_* del .env) -> guarda el accessToken
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@lastmile.cl","password":"<BOOTSTRAP_ADMIN_PASSWORD>"}'

# 2. El ADMIN crea un despachador (solo un ADMIN puede asignar roles)
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer <accessToken del ADMIN>" \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Ana Torres","email":"ana@lastmile.cl","password":"clave12345","role":"DISPATCHER"}'

#    Login del despachador -> usa su accessToken en los pasos siguientes
#    (no hay registro público: toda cuenta la crea un ADMIN, #44)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@lastmile.cl","password":"clave12345"}'

# 3. Importar una planilla de envíos (columnas: cliente, telefono, direccion,
#    comuna, latitud, longitud, codigo_externo, tracking, fecha_entrega, notas)
curl -X POST http://localhost:8080/api/import/shipments \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@envios.xlsx"

# 4. Construir la ruta óptima del día
curl -X POST http://localhost:8080/api/routes/build \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-09-05","depotLat":-33.4489,"depotLng":-70.6693}'

# 5. Seguimiento público (sin token) por trackingCode
curl http://localhost:8080/api/tracking/ABC123
```

## Seguridad

- Contraseñas: **bcrypt** (factor de trabajo 12), nunca se persisten en texto plano.
- Autenticación: **JWT** (HS256) con access token (1h) + refresh token (7 días).
- Cuentas: LastMile es B2B y **no hay registro público** (#44).
  `POST /api/auth/register` responde 404 salvo que
  `PUBLIC_REGISTRATION_ENABLED=true`, y aun así solo crea `CLIENT`. Las
  cuentas las crea un `ADMIN` con `POST /api/users`.
- Permisos: "denegar por defecto". En `delivery-service` e `import-service`
  todo lo no público exige un rol interno (`ADMIN`, `DISPATCHER`, `OPERATOR`
  o `DRIVER`); `CLIENT` solo accede al seguimiento público y a health. Tabla
  completa en [`docs/api/README.md`](docs/api/README.md#permisos-44-denegar-por-defecto). El primer ADMIN se crea al arrancar desde
  `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`, únicamente si no existe
  ninguno.
- El `JWT_SECRET` se comparte entre `auth-service`, `delivery-service`,
  `api-gateway` e `import-service` vía variable de entorno — todos validan la
  misma firma sin tener que llamar de vuelta a `auth-service` en cada request.
- El gateway (`JwtAuthenticationFilter`) corta las requests no autenticadas en
  el borde y elimina siempre los `X-User-Id` / `X-User-Role` que manda el
  cliente (también en rutas públicas); solo propaga los que salen del JWT; cada microservicio además vuelve a validar el token (defensa en
  profundidad) y aplica autorización por rol (`ADMIN`, `DISPATCHER`, `DRIVER`,
  `OPERATOR`, `CLIENT`).
- Sin secretos en el repo: `docker-compose.yml` no trae contraseñas por
  defecto. `JWT_SECRET`, `POSTGRES_PASSWORD` y `REDIS_PASSWORD` se leen de
  `.env` (ver `.env.example`) y compose falla con un mensaje claro si falta
  alguna.
- Tampoco hay secretos por defecto en el código: si `JWT_SECRET` falta o tiene
  menos de 32 caracteres, los 4 servicios **no arrancan** y lo dicen
  (`APPLICATION FAILED TO START … JWT_SECRET no está definido`). La contraseña
  del datasource no tiene default. Los únicos valores fijos están en los
  tests (`src/test/resources/config/application.yml`).
- *Secret scanning*: el workflow `secret-scan.yml` corre
  [gitleaks](https://github.com/gitleaks/gitleaks) sobre todo el historial en
  cada PR. Las exclusiones (placeholders `reemplaza_...` y la semilla de demo)
  están en `.gitleaks.toml`. Para correrlo en local:
  `docker run --rm -v "$PWD:/repo" ghcr.io/gitleaks/gitleaks:v8.21.2 git /repo --config /repo/.gitleaks.toml`

## Documentación de APIs (OpenAPI)

| Servicio | OpenAPI JSON | Swagger UI |
|---|---|---|
| auth-service | http://localhost:8081/v3/api-docs | http://localhost:8081/swagger-ui.html |
| delivery-service | http://localhost:8082/v3/api-docs | http://localhost:8082/swagger-ui.html |

Más detalle en [`docs/api/`](docs/api/).

## Migraciones de base de datos (Flyway)

El esquema de `authdb` y `deliverydb` se versiona con **Flyway**; Hibernate
corre con `ddl-auto: validate` y **nunca** modifica tablas.

- Migraciones: `services/<servicio>/src/main/resources/db/migration/`
- Convención de nombres: `V<n>__<descripcion>.sql` (p.ej. `V2__agregar_ventana_cliente.sql`).
- Nunca edites una migración ya aplicada: crea una nueva versión.
- Las migraciones se ejecutan solas al arrancar el servicio.
- Si una entidad JPA y el esquema no coinciden, el servicio **no arranca**
  (y los tests de integración fallan): agrega la migración correspondiente.

Para recrear las bases desde cero (borra todos los datos locales):

```bash
docker compose down -v && docker compose up --build
```

> Bases creadas antes de Flyway (con `ddl-auto: update`) se marcan como
> versión 1 automáticamente (`baseline-on-migrate`), sin perder datos.

## Tests

```bash
# Unitarios + integración (Testcontainers levanta un PostgreSQL real: requiere Docker)
cd services/api-gateway && ./mvnw verify
cd services/auth-service && ./mvnw verify
cd services/delivery-service && ./mvnw verify
cd services/import-service && npm test
```

Qué cubren las pruebas base (HU-04):

| Servicio | Pruebas |
|---|---|
| api-gateway | `JwtAuthenticationFilterTest`: rutas públicas, token ausente, vencido, de refresh, con otra firma o inválido, y propagación de `X-User-Id` / `X-User-Role` |
| auth-service | `JwtUtilTest` (emisión y validación de access y refresh tokens), `UserRepositoryIT` |
| delivery-service | `JwtValidatorTest`, `TimeWindowLearningServiceTest`, `RouteOptimizationServiceTest` (OSRM simulado), `RepositoryIT` |
| import-service | `baseParsers.test.ts` (CSV y Excel), además de los tests de columnas, plantilla e importación |

**Cobertura:** JaCoCo mide delivery-service en `./mvnw verify` y deja el reporte
en `target/site/jacoco/index.html`. Si la cobertura de líneas del paquete
`algorithm/` baja del **60%**, `verify` falla (y con él el CI). El CI muestra el
porcentaje en el resumen del job y publica el reporte como artefacto
`jacoco-delivery-service`.

## Integración continua (GitHub Actions)

`.github/workflows/ci.yml` y `.github/workflows/secret-scan.yml` corren en cada
`pull_request` y en cada `push` a `main`, con estos jobs en paralelo:

| Job | Qué hace |
|---|---|
| `java (api-gateway \| auth-service \| delivery-service)` | JDK 21 con caché Maven, `./mvnw -B verify` (unit + integración con Testcontainers; en delivery-service, cobertura de `algorithm/` ≥ 60% con JaCoCo) |
| `node (services/import-service \| frontend)` | Node 20 con caché npm, `npm ci`, `npm run lint`, `npm test` (si existe; vitest en import-service), `npm run build` |
| `docker-build (×5)` | Construye las 5 imágenes del compose sin publicarlas |
| `gitleaks` | Busca secretos en todo el historial de git (config en `.gitleaks.toml`) |

Los `package-lock.json` están versionados: usa `npm ci` (no `npm install`)
para reproducir exactamente las mismas dependencias que el CI.

### Branch protection en `main` (configurada)

`main` está protegida con *classic branch protection* desde que el repo es
público:

- **Pull request obligatorio**: no se puede hacer push directo a `main`
  (0 aprobaciones requeridas, porque hay un solo mantenedor).
- **Status checks obligatorios** y rama **al día** con `main` antes del merge
  (`strict`). Un PR con un check en rojo queda bloqueado.
- **Aplica también a administradores** (`enforce_admins`).
- **Sin force-push ni borrado** de `main`.

Checks obligatorios (los 11 jobs de CI): `gitleaks`, `java (api-gateway)`,
`java (auth-service)`, `java (delivery-service)`,
`node (services/import-service)`, `node (frontend)` y los 5
`docker-build (...)`. Si se renombra o agrega un job, hay que actualizar esta
lista, o los PRs quedan esperando un check que nunca llega.

Configuración aplicada (revisa la actual con
`gh api repos/andresfloreso/last-mile/branches/main/protection`):

```bash
gh api -X PUT repos/andresfloreso/last-mile/branches/main/protection --input - <<'EOF'
{
  "required_status_checks": { "strict": true, "contexts": [
    "gitleaks",
    "java (api-gateway)", "java (auth-service)", "java (delivery-service)",
    "node (services/import-service)", "node (frontend)",
    "docker-build (api-gateway)", "docker-build (auth-service)", "docker-build (delivery-service)",
    "docker-build (import-service)", "docker-build (frontend)"
  ]},
  "enforce_admins": true,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
```

## Desarrollo local (sin Docker)

```bash
# Backend Java — requiere solo JDK 21 (JAVA_HOME); Maven lo trae el wrapper.
# Sin valores por defecto: exporta antes las variables de tu .env
set -a && . ./.env && set +a
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
cd services/auth-service && ./mvnw spring-boot:run
cd services/delivery-service && ./mvnw spring-boot:run
cd services/api-gateway && ./mvnw spring-boot:run

# import-service — requiere Node 20+
cd services/import-service && npm ci && npm run dev

# frontend — requiere Node 20+
cd frontend && npm ci && npm run dev
```

### Maven Wrapper

Cada servicio Java trae su Maven Wrapper (`mvnw`, `mvnw.cmd` y
`.mvn/wrapper/maven-wrapper.properties`), que fija **Maven 3.9.9** y verifica
el SHA-256 de la distribución. La primera ejecución la descarga en
`~/.m2/wrapper`; no hace falta instalar Maven. Docker y CI también usan el
wrapper, así que todos compilan con la misma versión.

| Sistema | Comando |
|---|---|
| Linux / macOS / Git Bash | `./mvnw verify` |
| Windows (PowerShell) | `.\mvnw.cmd verify` |
| Windows (cmd) | `mvnw verify` |

Para actualizar Maven en los tres servicios:

```bash
for s in api-gateway auth-service delivery-service; do
  (cd services/$s && ./mvnw -N wrapper:wrapper -Dmaven=<versión>)
done
# luego agrega distributionSha256Sum (del .zip) a cada maven-wrapper.properties
```

## Próximos pasos sugeridos

- Reemplazar el ruteo *nearest-neighbor* por un solver VRP más robusto
  (OR-Tools) cuando el volumen diario de paradas crezca.
- Agregar caché en Redis (ya viene en el `docker-compose`) para el perfil de
  ventana horaria por cliente, evitando recalcularlo en cada build de ruta.
- Integraciones e-commerce (Shopify, MercadoLibre, Bsale) como fuente
  adicional de creación de envíos, en paralelo a la importación de planillas.
- Tracking en tiempo real del camión (websocket) sobre el mapa del frontend
  con `react-leaflet` (ya incluido en las dependencias).
