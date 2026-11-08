# Sprint 1: cierre

- **Milestone:** Sprint 1 (16 nov – 27 nov 2026)
- **Objetivo:** base técnica confiable y roles seguros
- **Compromiso:** HU-01, HU-02, HU-03 y HU-05 (16 puntos) + reorganización del repo
- **Estado al 8 nov 2026:** desarrollo terminado; **todos los PRs abiertos y en revisión** (nada mergeado a `main`).

## Completado

| Historia | Pts | Issue | PR | Criterios | Evidencia |
|---|---|---|---|---|---|
| Reorganización del repo | – | #1 | #6 | 4/4 | `git mv` a `services/`, ADR 0001, `docker compose config` OK |
| HU-02 Migraciones con Flyway | 5 | #2 | #7 | 5/5 | `V1__baseline` en ambos servicios; `ddl-auto: validate`; `down -v` + `up --build` desde cero sin errores; 6 IT |
| HU-03 CI en GitHub Actions | 5 | #3 | #8 | 7/7 | 10/10 checks en verde (3 java, 2 node, 5 docker-build) |
| HU-05 Solo un ADMIN asigna roles | 3 | #4 | #10 | 9/9 | 9 unit + 8 IT: register con `role: ADMIN` → CLIENT; 401 / 403 / 201 |
| HU-01 Datos semilla y healthchecks | 3 | #5 | #13 | 8/8 | 7/7 contenedores healthy; la ruta pone primero a los 13 envíos de clientes de la mañana |

**Puntos completados (pendientes de merge): 16/16.**

Fuera del plan:

- #11 / PR #14: corrección de `GET /api/shipments`, que respondía 500 por el proxy LAZY de Hibernate. Ahora devuelve DTOs. Pedido durante el sprint para poder ver datos en el frontend.

## Orden de merge

Los PRs están apilados porque cada historia depende de la anterior:

```
main ← #6 chore ← #7 HU-02 ← #8 HU-03 ← #10 HU-05 ← #13 HU-01
                                    ↖ #14 fix (#11)
```

Mergear en ese orden. Al mergear cada uno, GitHub reapunta el siguiente a
`main` automáticamente. Para que eso pase, borra la rama al mergear (o activa
*Automatically delete head branches* en Settings).

## Pendiente

- Revisión y merge de los PRs #6, #7, #8, #10, #13 y #14.
- **Branch protection:** el repo ya es público, así que las reglas de rama se
  aplican: los 11 checks de CI son obligatorios en `main` (ver README,
  "Branch protection en `main`").
- Configurar `.env` real (`JWT_SECRET`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`,
  `BOOTSTRAP_ADMIN_*`) en cada ambiente: compose ya no trae valores por defecto.

## Hallazgos (ver `hallazgos.md`)

| Issue | Tipo | Resumen |
|---|---|---|
| #9 | bug | El gateway no elimina los headers `X-User-Id` / `X-User-Role` que envía el cliente |
| #12 | bug | delivery-service responde 500 y expone SQL ante JSON inválido o tracking duplicado |
| #11 | bug | `GET /api/shipments` 500 por proxy lazy (**corregido** en #14) |

## Riesgos para el Sprint 2

1. **Cadena de PRs larga.** Mientras no se mergeen, cada rama nueva debe
   salir de la punta de la cadena (`feature/HU-01-seed`), y un cambio pedido en
   revisión obliga a propagarlo con merges hacia arriba.
2. **El frontend no lista envíos existentes.** El RouteBuilder solo trabaja
   con paradas agregadas a mano, y los 50 envíos semilla solo se ven al
   optimizar. Se resuelve con HU-10 (S3), pero afecta las demos de S2.
3. **HU-08 depende de #12.** El reporte de errores por fila muestra el
   `message` que devuelve delivery-service; hoy algunos de esos mensajes son
   SQL crudo.
4. **HU-09 (Nominatim)** necesita caché y límite de 1 req/s. Los tests deben
   apuntar a un servidor falso (como ya hacen los IT con `http://localhost:1`).
5. **HU-04 (cobertura ≥60% en `algorithm/`)** ya tiene una base:
   `SeedDataIT` valida el motor contra patrones conocidos. Falta un harness de
   cobertura (JaCoCo) en el CI.
6. **Herramientas locales.** La máquina de desarrollo no tiene JDK ni `gh`
   instalados (en este sprint se usaron copias portables). Maven ya no hace
   falta: cada servicio trae Maven Wrapper (`./mvnw`, `mvnw.cmd`).
