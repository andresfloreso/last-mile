# Documentación de APIs

Cada servicio Spring Boot publica su contrato OpenAPI 3 con
[springdoc-openapi](https://springdoc.org/). El contrato se genera desde el
código y es la fuente de verdad, así que no se edita a mano.

| Servicio | OpenAPI JSON | Swagger UI |
|---|---|---|
| auth-service | `http://localhost:8081/v3/api-docs` | `http://localhost:8081/swagger-ui.html` |
| delivery-service | `http://localhost:8082/v3/api-docs` | `http://localhost:8082/swagger-ui.html` |

> Swagger UI se consulta directamente en el puerto del servicio; el gateway
> solo enruta `/api/**`.

## Endpoints de auth-service

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/login` | Público | Devuelve access token y refresh token |
| POST | `/api/auth/refresh` | Público (refresh token) | Renueva el access token y el refresh token. Responde `401` si el refresh token venció o el usuario está deshabilitado. El frontend lo llama solo ante un `401` y reintenta la request (HU-06) |
| POST | `/api/users` | `ADMIN` | Crea un usuario con cualquier rol (HU-05). Es la única forma de crear cuentas |
| POST | `/api/auth/register` | Desactivado (404) | LastMile es B2B: no hay registro público (#44). Solo existe con `PUBLIC_REGISTRATION_ENABLED=true`, y entonces crea **CLIENT** (el campo `role` se ignora) |

## Permisos (#44: denegar por defecto)

En `delivery-service` e `import-service`, todo lo que no es público exige un
rol interno (`ADMIN`, `DISPATCHER`, `OPERATOR` o `DRIVER`). `CLIENT` solo llega
a lo público. Un endpoint sin regla propia queda cerrado a `CLIENT`.

| Servicio | Ruta | ADMIN | DISPATCHER | OPERATOR | DRIVER | CLIENT / sin token |
|---|---|:-:|:-:|:-:|:-:|:-:|
| delivery | `GET /api/tracking/{codigo}`, `GET /actuator/health` | ✓ | ✓ | ✓ | ✓ | ✓ (público) |
| delivery | `POST /api/shipments/customers`, `POST /api/shipments`, `GET /api/shipments`, `PATCH`/`DELETE /api/shipments/{id}`, `POST /api/shipments/{id}/duplicate` | ✓ | ✓ | ✓ | — | — |
| delivery | `POST /api/shipments/attempts` (intentos de entrega) | ✓ | ✓ | ✓ | ✓ | — |
| delivery | `GET /api/customers/{id}`, `GET /api/customers/{id}/time-window` | ✓ | ✓ | ✓ | ✓ | — |
| delivery | `PUT /api/customers/{id}/declared-window`, `PUT /api/customers/{id}/location` | ✓ | ✓ | ✓ | — | — |
| delivery | `GET /api/routes` (ruta guardada, HU-32) | ✓ | ✓ | ✓ | — | — |
| delivery | `POST /api/routes/build` | ✓ | ✓ | — | — | — |
| delivery | `GET /api/vehicles`, `GET /api/vehicles/{id}` (HU-29) | ✓ | ✓ | ✓ | — | — |
| delivery | `POST /api/vehicles`, `PUT /api/vehicles/{id}` (HU-29) | ✓ | ✓ | — | — | — |
| delivery | `GET /api/geocoding/search` y cualquier otra ruta | ✓ | ✓ | ✓ | ✓ | — |
| import | `GET /api/import/template`, `POST /api/import/shipments` | ✓ | ✓ | ✓ | — | — |
| import | `GET /actuator/health` | ✓ | ✓ | ✓ | ✓ | ✓ (público) |

`—` = 403 con token válido; 401 sin token. Swagger sigue público en desarrollo
(se desactiva fuera de desarrollo en #43).

### `POST /api/users`

```json
{ "fullName": "Pedro Soto", "email": "pedro@lastmile.cl", "password": "clave12345", "role": "DRIVER" }
```

| Código | Cuándo |
|---|---|
| 201 | Usuario creado; devuelve `{id, fullName, email, role}` |
| 400 | Validación fallida o rol inexistente |
| 401 | Sin token, token inválido o expirado |
| 403 | El usuario autenticado no es ADMIN |
| 409 | El email ya está registrado |

## Locales en delivery-service (HU-25)

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/shipments/customers` | `ADMIN`, `DISPATCHER`, `OPERATOR` | Crea un local o devuelve el existente con el mismo `externalCode`. Acepta `businessType`, `declaredWindowStart`/`End` y el segundo tramo `declaredWindow2Start`/`End`; si el local ya existía y la request trae tipo o modulación, los actualiza |
| GET | `/api/customers/{id}` | `ADMIN`, `DISPATCHER`, `OPERATOR`, `DRIVER` | Detalle del local, con su tipo y su modulación. `CLIENT` recibe 403 (#38) |
| PUT | `/api/customers/{id}/declared-window` | `ADMIN`, `DISPATCHER`, `OPERATOR` | Reemplaza la modulación (uno o dos tramos); todo en `null` la elimina |
| PUT | `/api/customers/{id}/location` | `ADMIN`, `DISPATCHER`, `OPERATOR` | Fija `latitude`/`longitude` a mano y quita la marca "revisar" (HU-09) |

```json
{ "start": "09:00", "end": "12:00", "start2": "16:00", "end2": "18:00" }
```

Respuesta (`CustomerResponse`):

```json
{
  "id": "5eed0000-0000-4000-8000-000000000004",
  "fullName": "Bar La Esquina de Ñuñoa",
  "businessType": "BAR",
  "declaredWindow": { "start": "09:00", "end": "12:00", "start2": null, "end2": null },
  "...": "..."
}
```

| Código | Cuándo |
|---|---|
| 400 | Tramo incompleto, inicio posterior al término, segundo tramo solapado, hora con formato inválido o `businessType` desconocido |
| 403 | Rol sin permiso: `CLIENT` en el detalle; `DRIVER` o `CLIENT` al editar |
| 404 | El local no existe |

### Geocodificación al importar (HU-09)

Si la fila (o el formulario) no trae `latitude` y `longitude`, delivery-service
busca la dirección en [Nominatim](https://nominatim.org/release-docs/latest/api/Search/)
(`direccion, comuna, Chile`). `CustomerResponse.geocodeStatus` dice de dónde
salieron las coordenadas:

| `geocodeStatus` | Significado |
|---|---|
| `PROVIDED` | Las entregó el usuario (planilla con latitud/longitud, pin en el mapa o `PUT .../location`). Nunca se sobrescriben |
| `GEOCODED` | Nominatim ubicó el número de calle o el edificio (`place_rank` ≥ 28) |
| `REVIEW` | Ubicación aproximada (solo la calle, el barrio o la comuna) o dirección no encontrada (sin coordenadas). El mapa la marca "Revisar" |

- **1 request por segundo:** las consultas van de a una y entre el fin de una y el
  inicio de la siguiente pasa al menos `GEOCODING_MIN_INTERVAL` (1 s por defecto,
  como exige el servidor público). Una planilla de 300 direcciones nuevas tarda
  unos 5 minutos.
- **Caché por dirección:** en memoria, normalizada (mayúsculas y espacios no
  cuentan). Una dirección repetida no vuelve a consultar Nominatim; tampoco una
  sin resultados. Los errores de red no se cachean.
- Un local `REVIEW` se corrige con `PUT /api/customers/{id}/location` o
  reimportando la fila con latitud y longitud.
- Variables: `NOMINATIM_URL`, `NOMINATIM_USER_AGENT` (la política de Nominatim
  exige identificar la app y un contacto), `GEOCODING_MIN_INTERVAL` y
  `GEOCODING_MIN_PLACE_RANK` (ver `.env.example`).

## Listado de envíos (HU-10)

`GET /api/shipments` (`ADMIN`, `DISPATCHER`, `OPERATOR`; ver Permisos) devuelve una página de envíos, cada uno con
su local embebido. Todos los filtros son opcionales y se combinan con AND.

| Parámetro | Ejemplo | Descripción |
|---|---|---|
| `date` | `2026-12-15` | Un solo día programado. No se combina con `from`/`to` |
| `from`, `to` | `2026-12-01`, `2026-12-15` | Rango de días, inclusive. Se puede usar solo uno de los dos |
| `status` | `PENDING` | Uno o más estados: `status=PENDING&status=ROUTED` o `status=PENDING,ROUTED` |
| `commune` | `nunoa` | Comuna del local, exacta, sin distinguir mayúsculas ni tildes |
| `q` | `roble` | Busca en el nombre del local, su dirección, el tracking y el código externo |
| `page` | `0` | Página, desde 0 (por defecto 0) |
| `size` | `20` | De 1 a 500 (por defecto 20). 500 alcanza para la ruta completa de un día |

Orden: fecha más reciente primero; dentro de un día, por posición en la ruta
(los que no tienen posición van al final) y luego por tracking.

```json
{
  "content": [ { "trackingCode": "SEED-20261109-01", "status": "PENDING", "customer": { "fullName": "Minimarket Don Lucho", "...": "..." }, "...": "..." } ],
  "page": 0, "size": 20, "totalElements": 50, "totalPages": 3
}
```

Responde 400 si una fecha o un estado son inválidos, si se combina `date` con
`from`/`to`, si `from` es posterior a `to` o si `page`/`size` están fuera de rango.

> Hasta el Sprint 2 este endpoint exigía `date` y devolvía un arreglo. La
> pantalla de rutas usa ahora `size=500` y lee `content`.

## Modulación vs. ventana real (HU-11)

`GET /api/customers/{id}/time-window` (`ADMIN`, `DISPATCHER`, `OPERATOR`,
`DRIVER`) devuelve, para un local, lo que declara y lo que muestran los
intentos de entrega.

```json
{
  "customerName": "Bar La Esquina de Ñuñoa",
  "declaredWindow": { "start": "09:00", "end": "12:00", "start2": null, "end2": null },
  "realWindow": { "start": "18:00", "end": "20:00", "acceptedVisits": 14, "visits": 14, "basis": "LEARNED" },
  "summary": "Recibió 14 de 14 visitas entre 18:00 y 20:00",
  "totalAttempts": 20, "acceptedAttempts": 14, "rejectedAttempts": 6,
  "hoursToAvoid": [11, 15, 17],
  "histogram": [ { "hour": 0, "accepted": 0, "rejected": 0 }, "… 24 casilleros, 0 a 23 h" ]
}
```

- **Una sola fuente de verdad:** `realWindow` sale de
  `TimeWindowLearningService.learn()`, el mismo cálculo que usa
  `POST /api/routes/build`. `TimeWindowApiIT` verifica, parada por parada de la
  ruta semilla, que ambas coinciden.
- La ventana se explica con visitas ("X de Y visitas en esa franja"), nunca con
  un puntaje: `acceptedVisits` / `visits` cuentan los intentos dentro de
  `[start, end)`.
- `basis`: `LEARNED` si hay intentos; `DEFAULT` si no los hay, y entonces la
  ruta usa el horario comercial por defecto (09:00–18:00). HU-13 agregará la
  modulación y el perfil por comuna como base para locales con poco historial.
- `histogram`: hora de Santiago. `rejected` agrupa local cerrado, rechazo y
  "pidió otro horario".

## Ruta del día con hora estimada (HU-14)

`POST /api/routes/build` (`ADMIN`, `DISPATCHER`) ordena los envíos de la fecha,
guarda el orden y devuelve cada parada con su hora estimada.

```json
{ "date": "2026-12-15", "depotLat": -33.446393, "depotLng": -70.624979,
  "departureTime": "08:00", "stopMinutes": 10 }
```

`departureTime` (por defecto `routing.default-departure`, 08:00) y
`stopMinutes` (1–120, por defecto `routing.stop-minutes`, 10) son opcionales.
La respuesta sigue siendo un arreglo de paradas en orden, con estos campos
nuevos:

| Campo | Significado |
|---|---|
| `estimatedArrival` | Llegada del camión: salida anterior + tramo |
| `serviceStart` | Inicio de la entrega: la llegada, o la apertura de la ventana real si llega antes |
| `waitMinutes` | Espera hasta que abre la ventana real. Llegar antes y esperar cuenta como dentro de ventana (lo realista en B2B) |
| `estimatedDeparture` | `serviceStart` + `stopMinutes` |
| `travelMinutes`, `legDistanceMeters` | Tramo desde la parada anterior (o el depósito) |
| `outsideWindow` | La entrega empezaría cuando la ventana real ya cerró |
| `dayOffset` | 1 si la ruta no alcanza en el día y la llegada es al día siguiente |
| `path` | Tramo como pares `[lat, lng]`, para la polilínea |
| `timingSource` | `OSRM` (por calle), `ESTIMATED` (OSRM no respondió: línea recta × 1,3 a `routing.fallback-speed-kmh`, 25 km/h) o `NO_LOCATION` (local sin coordenadas) |

La **hora estimada** que muestra la pantalla es `serviceStart` (llegada +
espera), con "espera N min" debajo cuando corresponde.

La hora estimada es una capa aparte (`RouteTimingService`) que no reordena.

### Optimizador VRPTW (HU-28)

El orden lo decide un `RouteOptimizer`. `routing.optimizer.engine` elige cuál:

| Valor | Optimizador |
|---|---|
| `timefold` (por defecto) | VRPTW de un camión con Timefold Solver: parte del nearest-neighbor y lo mejora con búsqueda tabú |
| `nearest-neighbor` | El orden por franja y cercanía de HU-04 (`RouteOptimizationService`, sin cambios) |

El VRPTW, de mayor a menor prioridad:

1. Respeta la capacidad en kg y m³ y la jornada del camión. Son restricciones
   duras.
2. Respeta las paradas Primera y Última.
3. Minimiza las paradas fuera de su ventana real.
4. Minimiza los minutos fuera de ventana.
5. Minimiza los km.

Si el solver falla, o la mejor ruta no cabe en el camión o no termina dentro de
su jornada, usa el nearest-neighbor como respaldo y dice por qué.

El cuerpo acepta además `vehicleId` (opcional): el camión de la ruta. Sin él,
usa el vehículo activo de mayor capacidad en kg. Un vehículo inexistente da
`404`; uno inactivo, `400`.

Campos nuevos en cada parada, y una cabecera:

| Campo | Significado |
|---|---|
| `optimizer` | `VRPTW` o `NEAREST_NEIGHBOR`: el que ordenó la ruta |
| `optimizerNote` | Por qué se usó el respaldo (por ejemplo, "Sin solución factible para el camión KXPT-21: la carga (4100 kg) supera su capacidad (3500 kg)…"); `null` si no hizo falta |
| `vehiclePlate` | Patente del camión de la ruta; `null` si no hay vehículos |
| Cabecera `X-Route-Optimizer` | El mismo valor que `optimizer` |

Configuración (en `application.yml`):

| Propiedad | Por defecto | Qué hace |
|---|---|---|
| `routing.optimizer.engine` | `timefold` | Optimizador a usar |
| `routing.optimizer.time-limit` | `10s` | Tiempo máximo del solver por ruta |
| `routing.optimizer.unimproved-time-limit` | `3s` | Corta antes si pasa este tiempo sin mejora |

El VRPTW pide a OSRM una sola tabla de tiempos y distancias (`/table`). Si OSRM
no responde, usa línea recta × 1,3 a `routing.fallback-speed-kmh`. La medición
antes/después está en
[`docs/plan/hu-28/antes-despues.md`](../plan/hu-28/antes-despues.md).

### Ruta guardada, sin efectos (HU-32)

`GET /api/routes?date=2027-01-04&depotLat=-33.446393&depotLng=-70.624979`
(`ADMIN`, `DISPATCHER`, `OPERATOR`) devuelve la ruta que guardó el último
`POST /api/routes/build`: las mismas paradas, en el mismo orden y con la misma
hora estimada.

- **No escribe nada:** no llama al optimizador ni cambia la posición o el
  estado de ningún envío.
- **Parámetros:** `departureTime` (HH:mm) y `stopMinutes` (1–120) son
  opcionales, como en `build`.
- **Envíos sin posición:** si un envío no tiene posición (por ejemplo, se
  importó después de armar la ruta), no aparece. La pantalla avisa cuántos
  faltan.
- **Fecha sin ruta armada:** devuelve `[]`.
- **Campos en `null`:** `optimizer`, `optimizerNote` y `vehiclePlate`, porque
  no se guardan.

La pantalla de rutas usa este `GET` al abrir y al cambiar de fecha. Solo
llama a `build` cuando despacho aprieta "Armar ruta" o "Reoptimizar", o
corrige la ubicación de un local.

La hora estimada pide a OSRM una `route` por orden de paradas. Las últimas 64
quedan en memoria por 6 h (`routing.osrm-route-cache.size` y `.ttl`). Volver a
abrir un día no consulta OSRM mientras el orden no cambie. La medición está en
[`docs/plan/hu-32/medicion.md`](../plan/hu-32/medicion.md).

### Paradas consolidadas (HU-30)

Los envíos del mismo local en la misma fecha forman **una sola parada**: el
camión visita el local una vez y entrega todo. La respuesta trae una parada
por local:

| Campo | Significado |
|---|---|
| `shipmentId` | Envío representante de la parada (el que ve el optimizador) |
| `shipmentIds` | Todos los envíos de la parada; el representante va primero |
| `shipmentCount` | Cantidad de envíos de la parada |
| `packageCount` | Suma de los bultos de sus envíos |
| `weightKg`, `volumeM3` | Suma del peso (kg) y del volumen (m³) de sus envíos (HU-29) |

El representante define la preferencia de orden de la parada: si algún envío
pide **Primera**, la parada va primera; si no, si alguno pide **Última**, va
última; si no, es automática. Todos los envíos de la parada quedan con la
misma `routePosition`.

## Vehículos y carga (HU-29)

### Vehículos

Un **vehículo** es un camión de la distribuidora, con patente, capacidad (kg
y m³) y jornada.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/vehicles?includeInactive=false` | Lista por patente; por defecto, solo los activos |
| GET | `/api/vehicles/{id}` | Detalle |
| POST | `/api/vehicles` | Registra uno (201). Patente repetida: 409 |
| PUT | `/api/vehicles/{id}` | Reemplaza sus datos. Con `"active": false` lo da de baja sin borrarlo |

```json
{ "plate": "KXPT-21", "name": "Camión 3/4", "capacityKg": 3500, "capacityM3": 18,
  "shiftStart": "08:00", "shiftEnd": "21:00", "active": true }
```

Reglas de los datos:

- La patente se guarda en mayúsculas y sin espacios (`kxpt 21` → `KXPT21`).
- Las capacidades deben ser mayores que 0.
- La jornada debe terminar después de empezar.
- Si un dato no cumple, la API responde 400.

### Peso y volumen por envío

Los envíos tienen `weightKg` (kg) y `volumeM3` (m³), los dos mayores que 0.
Se informan en `POST /api/shipments`, se editan con
`PATCH /api/shipments/{id}` y vienen en `GET /api/shipments`.

**Valor por defecto: 10 kg y 0,05 m³ por envío.**

- **Envíos que ya existían** antes de la migración V4: quedaron con ese
  valor.
- **Envíos nuevos sin peso o volumen** (de la API o de una planilla sin
  `peso_kg`/`volumen_m3`): toman `shipments.default-weight-kg` y
  `shipments.default-volume-m3` de la configuración de delivery-service.
  Por defecto son los mismos valores.

## Planilla de importación (import-service)

`GET /api/import/template` (`ADMIN`, `DISPATCHER`, `OPERATOR`) descarga la
plantilla `.xlsx`, con una hoja "Envíos" y otra "Instrucciones". Columnas:

`cliente`\*, `tipo_local`, `telefono`, `direccion`\*, `comuna`, `latitud`,
`longitud`, `codigo_externo`, `modulacion_desde`, `modulacion_hasta`,
`tracking`\*, `fecha_entrega`\*, `peso_kg`, `volumen_m3`, `notas`
(\* obligatoria).

`tipo_local` acepta botillería, minimarket, bar, restaurant (o restaurante) u
otro. La modulación se escribe en HH:mm (también acepta `9`, `9:30` o una celda
con formato de hora de Excel). Si una fila trae un tipo o una modulación
inválidos, se informa con su número de fila y no se importa.

`peso_kg` y `volumen_m3` (HU-29) aceptan coma o punto decimal (`24,5`).
- Si faltan, se usa el valor por defecto (10 kg y 0,05 m³).
- Si no son un número mayor que 0, la fila se informa y no se importa.

### Resumen de importación (HU-08)

`POST /api/import/shipments` (multipart, campo `file`) responde **200** con el
resumen, aunque algunas filas fallen:

```json
{
  "totalRows": 4, "created": 2, "skipped": 1, "failed": 1, "toReview": 1,
  "skippedRows": [{ "rowNumber": 3, "trackingCode": "PED-2", "message": "Ya existe un envío con el trackingCode PED-2" }],
  "errors":      [{ "rowNumber": 5, "trackingCode": "PED-4", "message": "tipo_local inválido: \"kiosco\" (...)" }],
  "reviewRows":  [{ "rowNumber": 2, "trackingCode": "PED-1", "message": "Ubicación aproximada: revisa el local en el mapa" }]
}
```

- **Omitidas** (`skippedRows`): el envío ya existía (mismo `tracking`). Reimportar la misma planilla no duplica envíos ni
  locales (los locales se reutilizan por `codigo_externo`).
- **Errores** (`errors`): fila inválida en la planilla o rechazada por delivery-service, con su número de fila y el motivo.
- **Por revisar** (`toReview`, `reviewRows`, HU-09): filas sin coordenadas cuyo local quedó con ubicación aproximada o
  sin ubicación. El envío se crea igual; el motivo dice cuál de los dos casos es.
- **422**: problema del archivo completo (no se puede leer, no tiene hojas o faltan columnas obligatorias).
