# ADR 0001 — Arquitectura de microservicios

- **Estado:** Aceptada (documenta la arquitectura existente)
- **Fecha:** 2026-11-08
- **Contexto del sprint:** Sprint 1, reorganización del repositorio

## Contexto

LastMile es una plataforma de logística de última milla. Su diferenciador es
aprender, por cliente, en qué ventana horaria suele recibir o rechazar un
envío, y usar ese aprendizaje para ordenar las paradas de la ruta del día.

El sistema ya está construido como un conjunto de servicios independientes
orquestados con `docker-compose`. Este ADR deja por escrito esa arquitectura
para que las decisiones siguientes (migraciones, CI, roles, datos semilla)
partan de una base explícita.

## Decisión

Se mantiene una arquitectura de microservicios con un API Gateway como punto
único de entrada:

| Componente | Tecnología | Responsabilidad | Persistencia |
|---|---|---|---|
| `services/api-gateway` | Java 21, Spring Cloud Gateway | Enrutamiento, validación JWT en el borde, propagación de `X-User-Id` / `X-User-Role` | — |
| `services/auth-service` | Java 21, Spring Boot 3, Spring Security | Registro, login, refresh; bcrypt (factor 12); emisión de JWT HS256 | `authdb` |
| `services/delivery-service` | Java 21, Spring Boot 3, JPA | Clientes, envíos, intentos de entrega, aprendizaje de ventanas horarias y optimización de rutas; geocoding con Nominatim y distancias con OSRM (fallback Haversine) | `deliverydb` |
| `services/import-service` | Node 20, TypeScript, Express | Importación de planillas `.xlsx` (ExcelJS) y `.csv` (PapaParse) hacia `delivery-service` | — (sin estado) |
| `frontend` | React, Vite, TypeScript, Tailwind, Zustand, React-Leaflet | Panel de despacho: login, importación, armado de rutas en mapa | — |
| PostgreSQL 16 | — | Una base por servicio con estado (`authdb`, `deliverydb`) | — |
| Redis 7 | — | Provisionado; aún sin uso (candidato a caché de perfiles de ventana horaria) | — |

### Principios

1. **Base de datos por servicio.** `auth-service` y `delivery-service` no
   comparten tablas; se comunican solo por HTTP.
2. **JWT compartido, sin llamada de vuelta.** Todos los servicios validan la
   firma HS256 con el mismo `JWT_SECRET`, así que ninguno consulta a
   `auth-service` en cada request.
3. **Defensa en profundidad.** El gateway corta el tráfico no autenticado y
   cada servicio vuelve a validar el token y aplica su autorización por rol
   (`ADMIN`, `DISPATCHER`, `DRIVER`, `OPERATOR`, `CLIENT`).
4. **Dependencias externas tolerantes a fallos.** Si OSRM o Nominatim no
   responden, la ruta se calcula igual con Haversine o se deja el cliente sin
   coordenadas; nunca se bloquea la operación.
5. **Políglota por necesidad.** `import-service` está en Node porque el
   ecosistema de parsing de planillas (ExcelJS, PapaParse) es más maduro ahí.

### Flujo principal

```
Frontend ──HTTP──▶ api-gateway ──▶ auth-service      (login / registro)
                              ├──▶ delivery-service  (envíos, rutas, tracking)
                              └──▶ import-service ──▶ delivery-service
```

## Estructura del repositorio

Los servicios backend viven en `services/`, el frontend en `frontend/`, la
infraestructura local en `infra/` y la documentación en `docs/` (`adr/`,
`api/`, `plan/`).

## Consecuencias

**Positivas**

- Cada servicio se construye, prueba y despliega por separado.
- El motor de ventanas horarias (`delivery-service`) puede escalar sin
  arrastrar a `auth-service`.

**Negativas / deuda conocida al momento de este ADR**

- Esquema gestionado con `ddl-auto: update` (se aborda en HU-02 con Flyway).
- Sin tests ni CI (se aborda en HU-03).
- El registro público permite elegir rol (se aborda en HU-05).
- Sin datos semilla para desarrollo ni demo (se aborda en HU-01).
- Operar varios servicios exige observabilidad y healthchecks consistentes.
- Un secreto HS256 compartido implica que cualquier servicio comprometido
  podría emitir tokens. A futuro conviene evaluar RS256 con clave pública
  distribuida.
