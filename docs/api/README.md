# Documentación de APIs

Cada servicio Spring Boot publica su contrato OpenAPI 3 con
[springdoc-openapi](https://springdoc.org/). El contrato se genera desde el
código y es la fuente de verdad, así que no se edita a mano.

| Servicio | OpenAPI JSON | Swagger UI |
|---|---|---|
| auth-service | `http://localhost:8081/v3/api-docs` | `http://localhost:8081/swagger-ui.html` |

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
