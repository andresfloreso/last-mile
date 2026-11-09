# Datos semilla (HU-01)

> Solo para desarrollo y demo. Nunca actives el perfil `seed` en producción.

Flyway ejecuta `afterMigrate.sql` de cada carpeta después de cada `migrate`,
cuando el servicio corre con `SPRING_PROFILES_ACTIVE=seed`. Los scripts son
idempotentes.

`docker-compose.yml` **no** activa el perfil. Lo activa el
`docker-compose.override.yml` de la raíz, que está versionado y que
`docker compose up` aplica automáticamente en desarrollo: pone
`SPRING_PROFILES_ACTIVE=seed` y monta `infra/seed/<servicio>` en `/seed`
(solo lectura) en auth-service y delivery-service. La decisión está en
[ADR 0002](../../docs/adr/0002-datos-semilla-con-callbacks-flyway.md).

```
infra/seed/
├── auth/afterMigrate.sql       # authdb: un usuario por rol
└── delivery/afterMigrate.sql   # deliverydb: clientes, historial y envíos del día
```

## Usuarios (authdb)

Contraseña de todos: **`lastmile123`**

| Email | Rol |
|---|---|
| `admin@lastmile.cl` | ADMIN |
| `despacho@lastmile.cl` | DISPATCHER |
| `conductor@lastmile.cl` | DRIVER |
| `operador@lastmile.cl` | OPERATOR |
| `cliente@lastmile.cl` | CLIENT |

Si ya existe un usuario con ese email (por ejemplo, el ADMIN de
`BOOTSTRAP_ADMIN_EMAIL`), se conserva el existente con su contraseña.

## Clientes, historial y envíos (deliverydb)

- **30 clientes**, uno por comuna de Santiago, con coordenadas reales.
  `external_code` = `SEED-C01` … `SEED-C30`.
- **Historial de 60 días**: un intento cada 3 días por cliente (600 en total).
  ~70% en su franja buena (`ACCEPTED`) y ~30% en la mala (`REJECTED_NOT_HOME`).
  Es determinista: siempre se generan los mismos datos.
- **50 envíos para hoy** (`SEED-<yyyymmdd>-01` … `-50`): los clientes 1–20
  reciben 2 y los 21–30, 1. Si levantas el ambiente otro día, se crean los
  envíos de ese día.

### Patrones conocidos

| Patrón | Acepta | Rechaza | Franja que debe aprender el motor | Clientes |
|---|---|---|---|---|
| `MORNING` ("cliente A") | 09–11 h | 14–17 h | 09:00–11:00 | 1, 5, 9, 13, 17, 21, 25, 29 |
| `MIDDAY` | 12–14 h | 09–12 h | 12:00–14:00 | 2, 6, 10, 14, 18, 22, 26, 30 |
| `AFTERNOON` | 15–17 h | 09–12 h | 15:00–17:00 | 3, 7, 11, 15, 19, 23, 27 |
| `EVENING` ("cliente B") | después de las 19 h | 11, 15 y 17 h | 18:00–20:00 | 4, 8, 12, 16, 20, 24, 28 |

Ejemplos: el **cliente 1** (Andrea Muñoz, Santiago) es un cliente A, y el
**cliente 4** (Diego Contreras, Ñuñoa) es un cliente B.

`TimeWindowLearningService` evalúa bloques de 2 h que empiezan entre las 08 y
las 18 h, por eso el patrón `EVENING` se aprende como 18:00–20:00.

## Demo: la ruta pone primero a los clientes de la mañana

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"despacho@lastmile.cl","password":"lastmile123"}' | jq -r .accessToken)

curl -s -X POST localhost:8080/api/routes/build \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$(date +%F)\",\"depotLat\":-33.4489,\"depotLng\":-70.6693}" \
  | jq -r '.[] | "\(.position)\t\(.windowStart)\t\(.customerName)"'
```

Las primeras 13 paradas tienen ventana `09:00`, que corresponde a los clientes
`MORNING`. Después vienen las de 12:00, 15:00 y 18:00.

## Recrear la semilla desde cero

```bash
cp -n .env.example .env   # si aún no tienes .env (compose exige JWT_SECRET, POSTGRES_PASSWORD y REDIS_PASSWORD)
docker compose down -v && docker compose up --build
```

## Levantar sin datos semilla

```bash
# con el override de desarrollo, pero sin datos de ejemplo
SPRING_PROFILES_ACTIVE=default docker compose up --build

# sin el override (como en staging/producción)
docker compose -f docker-compose.yml up --build
```
