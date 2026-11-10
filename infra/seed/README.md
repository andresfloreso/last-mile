# Datos semilla (HU-01, HU-25, HU-31)

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

- **30 locales comerciales** (8 minimarkets, 8 botillerías, 7 restaurantes y
  7 bares) en una **zona de reparto compacta de 3 comunas contiguas**:
  Santiago, Providencia y Ñuñoa, 10 por comuna, dentro de una caja de unos
  6 × 6 km (HU-31). `external_code` = `SEED-C01` … `SEED-C30`. Cada local tiene
  **tipo** y **modulación** (horario de recepción declarado, HU-25).
- **Direcciones reales, nombres inventados.** Cada local está en una calle
  comercial real (por ejemplo Av. Providencia 2124 o Av. Irarrázaval 3550), con
  el número y las coordenadas que tiene en OpenStreetMap, fijas en el SQL: no se
  geocodifica nada en runtime. Los nombres de los locales son inventados y no
  hay datos personales. Ver "Cómo se verificaron las direcciones".
- **Bodega:** Av. Italia 1300, Providencia (`-33.446393, -70.624979`), dentro
  de la zona. Es el valor por defecto de `VITE_DEPOT_LAT` / `VITE_DEPOT_LNG`.
- **Historial de 60 días**: un intento cada 3 días por local (600 en total).
  Por local: 14 visitas en su ventana real y 6 fuera de ella
  (`REJECTED_NOT_HOME`, "Local cerrado"). **Dentro de la ventana no siempre
  recibe**: entre 1 y 4 de las 14 son "Encargado no estaba", así que acepta
  10, 11, 12 o 13 de 14 (71% a 93%; `1 + idx % 4` rechazos). Esos rechazos se
  reparten entre las dos horas de la ventana para que el motor aprenda la misma
  ventana. Es determinista: siempre se generan los mismos datos.
- **24 envíos para hoy** (`SEED-<yyyymmdd>-01` … `-24`): uno por local, salvo
  los 6 que hoy no piden (3, 6, 7, 11, 16 y 29), para que **un camión alcance a
  hacer la ruta en el horario** (mañana 7, mediodía 7, tarde 4, noche 6). Si levantas el ambiente
  otro día, se crean los envíos de ese día.
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
restaurantes 15–18 y bares 18–21. La **Botillería Chile España** (26) declara
además un segundo tramo, de 17 a 19 h.

**11 locales (más de un tercio) declaran otro horario**, como pasa en
terreno: dicen "recibo de 9 a 12", pero en verdad reciben en otra franja. Los
que reciben en la mañana declaran 15–18.

| # | Local | Comuna | Declara | Recibe de verdad |
|---|---|---|---|---|
| 3 | Restaurant El Encuentro | Providencia | 09–12 | 15–17 |
| 4 | Bar La Esquina de Ñuñoa ("local B") | Ñuñoa | 09–12 | después de las 19 |
| 6 | Botillería Los Leones | Providencia | 09–12 | 12–14 |
| 9 | Minimarket Los Andes | Ñuñoa | 15–18 | 09–11 |
| 12 | Bar Dublé Almeyda | Ñuñoa | 09–12 | después de las 19 |
| 15 | Restaurant Barrio Italia | Providencia | 09–12 | 15–17 |
| 18 | Botillería Portugal | Santiago | 09–12 | 12–14 |
| 21 | Minimarket Pedro de Valdivia | Providencia | 15–18 | 09–11 |
| 24 | Bar Bilbao | Providencia | 09–12 | después de las 19 |
| 27 | Restaurant San Diego | Santiago | 09–12 | 15–17 |
| 30 | Botillería Providencia | Providencia | 09–12 | 12–14 |

`SeedDataIT` verifica que el motor aprende la ventana real de los 30 locales,
y no la modulación declarada, también con el ruido dentro de la ventana.

> Los locales mantienen su UUID, su patrón y su historial. En el Sprint 4
> (HU-31) cambiaron de dirección, comuna y, varios, de nombre. Una base existente
> se actualiza sola al arrancar (`ON CONFLICT DO UPDATE`), pero para una base
> limpia, recréala con `docker compose down -v` (respalda antes con `pg_dumpall`
> en `backups/`).

Ejemplos: el **local 1** (Minimarket Don Lucho, Merced 300, Santiago) es un local A que
declara y recibe en la mañana. El **local 4** (Bar La Esquina de Ñuñoa, Av. Irarrázaval 3550) es un
local B: declara 09–12, pero el motor aprende 18:00–20:00.

## Demo: la ruta pone primero a los locales de la mañana

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"despacho@lastmile.cl","password":"lastmile123"}' | jq -r .accessToken)

curl -s -X POST localhost:8080/api/routes/build \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$(date +%F)\",\"depotLat\":-33.446393,\"depotLng\":-70.624979}" \
  | jq -r '.[] | "\(.position)\t\(.windowStart)\t\(.customerName)"'
```

Las primeras 7 paradas tienen ventana `09:00`, que corresponde a los locales
`MORNING` con pedido hoy. Después vienen las de 12:00, 15:00 y 18:00.

## Cómo se verificaron las direcciones (HU-31)

`tools/verificar_direcciones.py` revisa cada dirección de `tools/direcciones.json`
(los 30 locales y la bodega) contra servicios públicos de OpenStreetMap, a 1
request por segundo y con un User-Agent genérico:

1. **Nominatim** (búsqueda estructurada): la dirección existe con **ese número
   exacto** (`place_rank` 30) y en esa comuna.
2. **OSRM `/nearest`**: el punto queda a **menos de 40 m de una calle**
   transitable en auto (el máximo de la semilla es 35,5 m).
3. **Overpass `is_in`**: el punto **no cae dentro** de un parque, estadio,
   cancha, plaza de juegos, cementerio, universidad u hospital.

```bash
python -I infra/seed/tools/verificar_direcciones.py infra/seed/tools/direcciones.json /tmp/resultado.json
```

El resultado con el que se armó la semilla está en
`tools/direcciones-verificadas.json` (coordenadas, dirección según OSM,
distancia a la calle y calle más cercana). En la semilla anterior, por ejemplo,
"Av. Grecia 2040" caía dentro del Estadio Nacional, y Av. Providencia 2124
estaba unos 350 m corrida. Esta semilla descarta todo lo que no pasa los tres
chequeos. Cualquier dirección se puede abrir en Google Maps con su
`lat,lng`.

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
