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
  "totalRows": 4, "created": 2, "skipped": 1, "failed": 1,
  "skippedRows": [{ "rowNumber": 3, "trackingCode": "PED-2", "message": "Ya existe un envío con el trackingCode PED-2" }],
  "errors":      [{ "rowNumber": 5, "trackingCode": "PED-4", "message": "tipo_local inválido: \"kiosco\" (...)" }]
}
```

- **Omitidas** (`skippedRows`): el envío ya existía (mismo `tracking`). Reimportar la misma planilla no duplica envíos ni
  locales (los locales se reutilizan por `codigo_externo`).
- **Errores** (`errors`): fila inválida en la planilla o rechazada por delivery-service, con su número de fila y el motivo.
- **422**: problema del archivo completo (no se puede leer, no tiene hojas o faltan columnas obligatorias).
