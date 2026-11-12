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

- **40 locales comerciales** (10 minimarkets, 10 botillerías, 10 restaurantes
  y 10 bares) en una **zona de reparto compacta de 3 comunas contiguas**:
  Santiago (14), Providencia (13) y Ñuñoa (13), dentro de una caja de unos
  6 × 6 km (HU-31). `external_code` = `SEED-C01` … `SEED-C40`. Cada local tiene
  **tipo** y **modulación** (horario de recepción declarado, HU-25).
- **Direcciones reales, nombres inventados.** Cada local está en una calle
  comercial real (por ejemplo Av. Providencia 2124 o Av. Irarrázaval 3550), con
  el número y las coordenadas que tiene en OpenStreetMap, fijas en el SQL: no se
  geocodifica nada en runtime. Los nombres de los locales son inventados y no
  hay datos personales. Ver "Cómo se verificaron las direcciones".
- **Bodega:** Av. Italia 1300, Providencia (`-33.446393, -70.624979`), dentro
  de la zona. Es el valor por defecto de `VITE_DEPOT_LAT` / `VITE_DEPOT_LNG`.
- **Historial de 60 días**: un intento cada 3 días por local (800 en total).
  Se genera por local: si una base ya tenía historial de los locales 1–30,
  al arrancar se agrega solo el de los nuevos.
  Por local: 14 visitas en su ventana real y 6 fuera de ella
  (`REJECTED_NOT_HOME`, "Local cerrado"). **Dentro de la ventana no siempre
  recibe**: entre 1 y 4 de las 14 son "Encargado no estaba", así que acepta
  10, 11, 12 o 13 de 14 (71% a 93%; `1 + idx % 4` rechazos). Esos rechazos se
  reparten entre las dos horas de la ventana para que el motor aprenda la misma
  ventana. Es determinista: siempre se generan los mismos datos.
- **24 envíos para hoy** (`SEED-<yyyymmdd>-01` … `-24`): uno por local de los
  locales 1–30, salvo los 6 que hoy no piden (3, 6, 7, 11, 16 y 29), para que
  **un camión alcance a hacer la ruta en el horario** (mañana 7, mediodía 7,
  tarde 4, noche 6). Es la demo de la pantalla de rutas. Además, los locales
  1 y 4 reciben un segundo pedido (`-25` y `-26`): **26 envíos en 24
  paradas**, porque la ruta visita cada local una vez (HU-30).
- **Día cargado: lunes 4 de enero de 2027** (`SEED-CARGA-20270104-01` … `-40`):
  un envío por cada uno de los 40 locales, 10 por franja, más un segundo pedido
  en los locales 2, 5, 12, 19, 26 y 33 (`-41` … `-46`): 46 envíos en 40
  paradas (HU-30). Con un camión **no
  caben todos dentro de su ventana real**, así que es el día con el que HU-28
  compara optimizadores (km, hora de término y paradas y minutos fuera de
  ventana). La fecha es fija para que no se mezcle con los envíos de "hoy". Si levantas el ambiente
  otro día, se crean los envíos de ese día.
- **Camiones (HU-29):**

  | Patente | Vehículo | Capacidad | Jornada |
  |---|---|---|---|
  | `KXPT-21` | Camión 3/4 | 3.500 kg, 18 m³ | 08:00–21:00 (los bares reciben hasta las 20:00) |
  | `JRHL-48` | Furgón | 1.200 kg, 8 m³ | 08:00–18:00 |

  Si ya existen, no se tocan.
- **Peso y volumen de cada envío semilla** = bultos × lo que pesa y ocupa un
  bulto según el tipo de local. Se recalcula en cada arranque.

  | Tipo de local | kg por bulto | m³ por bulto |
  |---|---|---|
  | Botillería | 18 | 0,035 |
  | Bar | 16 | 0,040 |
  | Minimarket | 12 | 0,050 |
  | Restaurant | 10 | 0,060 |

  El día cargado (1.300 kg y 4,2 m³) cabe en el camión 3/4 pero no en el furgón.
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

**26 locales** declaran su ventana real: minimarkets 09–12, botillerías 12–15,
restaurantes 15–18 y bares 18–21. La **Botillería Chile España** (26) declara
además un segundo tramo, de 17 a 19 h.

**14 locales (más de un tercio) declaran otro horario**, como pasa en
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
| 33 | Minimarket Valdivia Norte | Providencia | 15–18 | 09–11 |
| 36 | Bar El Clavel | Ñuñoa | 09–12 | después de las 19 |
| 39 | Restaurant Santa Isabel | Santiago | 09–12 | 15–17 |

`SeedDataIT` verifica que el motor aprende la ventana real de los 40 locales,
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
(los 40 locales y la bodega) contra servicios públicos de OpenStreetMap, a 1
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

**Enganche a la calle (Sprint 5, #85).** `tools/revisar_enganche.py` revisa que
OSRM enganche cada local en la calle de su dirección y no en la transversal.
Los locales de esquina 2, 4, 19, 29, 31, 32, 33 y 38 se movieron entre 13 y
117 m al punto de su propia calle (ver `docs/plan/vueltas-ruta/medicion.md`).

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
