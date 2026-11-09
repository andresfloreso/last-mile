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
| POST | `/api/auth/register` | Público | Crea un usuario **CLIENT**; el campo `role` se ignora |
| POST | `/api/auth/login` | Público | Devuelve access token y refresh token |
| POST | `/api/auth/refresh` | Público (refresh token) | Renueva el access token |
| POST | `/api/users` | `ADMIN` | Crea un usuario con cualquier rol (HU-05) |

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
| POST | `/api/shipments/customers` | Autenticado | Crea un local o devuelve el existente con el mismo `externalCode`. Acepta `businessType`, `declaredWindowStart`/`End` y el segundo tramo `declaredWindow2Start`/`End`; si el local ya existía y la request trae tipo o modulación, los actualiza |
| GET | `/api/customers/{id}` | Autenticado | Detalle del local, con su tipo y su modulación |
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
| 403 | Rol sin permiso para editar |
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

`GET /api/shipments` (autenticado) devuelve una página de envíos, cada uno con
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

## Planilla de importación (import-service)

`GET /api/import/template` (`ADMIN`, `DISPATCHER`, `OPERATOR`) descarga la
plantilla `.xlsx`, con una hoja "Envíos" y otra "Instrucciones". Columnas:

`cliente`\*, `tipo_local`, `telefono`, `direccion`\*, `comuna`, `latitud`,
`longitud`, `codigo_externo`, `modulacion_desde`, `modulacion_hasta`,
`tracking`\*, `fecha_entrega`\*, `notas` (\* obligatoria).

`tipo_local` acepta botillería, minimarket, bar, restaurant (o restaurante) u
otro. La modulación se escribe en HH:mm (también acepta `9`, `9:30` o una celda
con formato de hora de Excel). Si una fila trae un tipo o una modulación
inválidos, se informa con su número de fila y no se importa.

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
