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
└── docker-compose.yml
```

Las decisiones de arquitectura se registran en [`docs/adr/`](docs/adr/).

## Servicios

| Servicio | Stack | Responsabilidad |
|---|---|---|
| `api-gateway` | Java, Spring Cloud Gateway | Punto único de entrada, valida JWT una sola vez y enruta a cada microservicio, propaga `X-User-Id` / `X-User-Role` |
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
# edita .env y define un JWT_SECRET real de al menos 32 caracteres

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

## Flujo típico de uso (vía gateway, puerto 8080)

```bash
# 1. Registrar un usuario despachador
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Ana Torres","email":"ana@lastmile.cl","password":"clave12345","role":"DISPATCHER"}'

# 2. Login -> guarda el accessToken
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
- El `JWT_SECRET` se comparte entre `auth-service`, `delivery-service`,
  `api-gateway` e `import-service` vía variable de entorno — todos validan la
  misma firma sin tener que llamar de vuelta a `auth-service` en cada request.
- El gateway (`JwtAuthenticationFilter`) corta las requests no autenticadas en
  el borde; cada microservicio además vuelve a validar el token (defensa en
  profundidad) y aplica autorización por rol (`ADMIN`, `DISPATCHER`, `DRIVER`,
  `OPERATOR`, `CLIENT`).

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
cd services/auth-service && mvn verify
cd services/delivery-service && mvn verify
```

## Integración continua (GitHub Actions)

`.github/workflows/ci.yml` corre en cada `pull_request` y en cada `push` a `main`,
con estos jobs en paralelo:

| Job | Qué hace |
|---|---|
| `java (api-gateway \| auth-service \| delivery-service)` | JDK 21 con caché Maven, `mvn -B verify` (unit + integración con Testcontainers) |
| `node (services/import-service \| frontend)` | Node 20 con caché npm, `npm ci`, `npm run lint`, `npm run build` |
| `docker-build (×5)` | Construye las 5 imágenes del compose sin publicarlas |

Los `package-lock.json` están versionados: usa `npm ci` (no `npm install`)
para reproducir exactamente las mismas dependencias que el CI.

### Exigir el CI antes del merge (branch protection)

1. En GitHub: **Settings → Branches → Add branch ruleset** (o *Add classic branch protection rule*).
2. *Target*: rama `main`.
3. Activa **Require a pull request before merging**.
4. Activa **Require status checks to pass** y agrega los checks
   `java (api-gateway)`, `java (auth-service)`, `java (delivery-service)`,
   `node (services/import-service)`, `node (frontend)` y los 5 `docker-build (...)`.
   Los checks aparecen en el buscador después de que el workflow corrió al menos una vez.
5. Activa **Require branches to be up to date before merging** y **Block force pushes**.

Con `gh` (equivalente, classic protection):

```bash
gh api -X PUT repos/<owner>/last-mile/branches/main/protection --input - <<'EOF'
{
  "required_status_checks": { "strict": true, "contexts": [
    "java (api-gateway)", "java (auth-service)", "java (delivery-service)",
    "node (services/import-service)", "node (frontend)",
    "docker-build (api-gateway)", "docker-build (auth-service)", "docker-build (delivery-service)",
    "docker-build (import-service)", "docker-build (frontend)"
  ]},
  "enforce_admins": true,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null,
  "allow_force_pushes": false
}
EOF
```

> En repositorios **privados**, las reglas de rama solo se aplican con GitHub
> Pro, Team o Enterprise. En el plan Free hay que hacer público el repo o
> cambiar de plan para que el bloqueo sea efectivo.

## Desarrollo local (sin Docker)

```bash
# Backend Java — requiere JDK 21 + Maven
cd services/auth-service && mvn spring-boot:run
cd services/delivery-service && mvn spring-boot:run
cd services/api-gateway && mvn spring-boot:run

# import-service — requiere Node 20+
cd services/import-service && npm ci && npm run dev

# frontend — requiere Node 20+
cd frontend && npm ci && npm run dev
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
