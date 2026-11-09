# Datos semilla (HU-01, HU-25)

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
└── delivery/afterMigrate.sql   # deliverydb: locales, historial y envíos del día
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

## Locales, historial y envíos (deliverydb)

- **30 locales comerciales** de Santiago (8 minimarkets, 8 botillerías, 7
  restaurantes y 7 bares), uno por comuna, con coordenadas reales.
  `external_code` = `SEED-C01` … `SEED-C30`. Cada local tiene **tipo** y
  **modulación** (horario de recepción declarado, HU-25).
- **Historial de 60 días**: un intento cada 3 días por local (600 en total).
  ~70% dentro de su ventana real (`ACCEPTED`) y ~30% fuera de ella
  (`REJECTED_NOT_HOME`, "Local cerrado"). Es determinista: siempre se generan
  los mismos datos.
- **50 envíos para hoy** (`SEED-<yyyymmdd>-01` … `-50`): los locales 1–20
  reciben 2 y los 21–30, 1. Si levantas el ambiente otro día, se crean los
  envíos de ese día.
- Si la base ya tenía la semilla de HU-01 (30 "clientes" personas), los
  locales se actualizan solos al arrancar: el script usa `ON CONFLICT DO UPDATE`.

### Ventana real (lo que aprende el motor)

| Patrón | Tipo de local | Recibe | Rechaza | Ventana real que aprende el motor | Locales |
|---|---|---|---|---|---|
| `MORNING` ("local A") | Minimarket | 09–11 h | 14–17 h | 09:00–11:00 | 1, 5, 9, 13, 17, 21, 25, 29 |
| `MIDDAY` | Botillería | 12–14 h | 09–12 h | 12:00–14:00 | 2, 6, 10, 14, 18, 22, 26, 30 |
| `AFTERNOON` | Restaurant | 15–17 h | 09–12 h | 15:00–17:00 | 3, 7, 11, 15, 19, 23, 27 |
| `EVENING` ("local B") | Bar | después de las 19 h | 11, 15 y 17 h | 18:00–20:00 | 4, 8, 12, 16, 20, 24, 28 |

`TimeWindowLearningService` evalúa bloques de 2 h que empiezan entre las 08 y
las 18 h, por eso el patrón `EVENING` se aprende como 18:00–20:00.

### Modulación (lo que el local declara)

**19 locales** declaran su ventana real: minimarkets 09–12, botillerías 12–15,
restaurantes 15–18 y bares 18–21. La **Botillería Huechuraba** (26) declara
además un segundo tramo, de 17 a 19 h.

**11 locales (más de un tercio) declaran otro horario**, como pasa en
terreno: dicen "recibo de 9 a 12", pero en verdad reciben en otra franja. Los
que reciben en la mañana declaran 15–18.

| # | Local | Declara | Recibe de verdad |
|---|---|---|---|
| 3 | Restaurant El Apoquindo | 09–12 | 15–17 |
| 4 | Bar La Esquina de Ñuñoa ("local B") | 09–12 | después de las 19 |
| 6 | Botillería Vitacura | 09–12 | 12–14 |
| 9 | Minimarket Los Andes | 15–18 | 09–11 |
| 12 | Bar La Gran Avenida | 09–12 | después de las 19 |
| 15 | Restaurant Lo Martínez | 09–12 | 15–17 |
| 18 | Botillería Cerrillos | 09–12 | 12–14 |
| 21 | Minimarket San Pablo | 15–18 | 09–11 |
| 24 | Bar Santa María | 09–12 | después de las 19 |
| 27 | Restaurant Independencia | 09–12 | 15–17 |
| 30 | Botillería Departamental | 09–12 | 12–14 |

`SeedDataIT` verifica que el motor aprende la ventana real de los 30 locales,
y no la modulación declarada.

Ejemplos: el **local 1** (Minimarket Don Lucho, Santiago) es un local A que
declara y recibe en la mañana. El **local 4** (Bar La Esquina de Ñuñoa) es un
local B: declara 09–12, pero el motor aprende 18:00–20:00.

## Demo: la ruta pone primero a los locales de la mañana

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"despacho@lastmile.cl","password":"lastmile123"}' | jq -r .accessToken)

curl -s -X POST localhost:8080/api/routes/build \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$(date +%F)\",\"depotLat\":-33.4489,\"depotLng\":-70.6693}" \
  | jq -r '.[] | "\(.position)\t\(.windowStart)\t\(.customerName)"'
```

Las primeras 13 paradas tienen ventana `09:00`, que corresponde a los locales
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
