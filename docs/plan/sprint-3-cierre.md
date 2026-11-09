# Sprint 3: cierre

- **Milestone:** Sprint 3 (14 dic – 25 dic 2026)
- **Objetivo:** pantalla de rutas nueva y ventana real visible. Al cierre se debe ver la ruta del día rediseñada, con hora estimada por parada y "declara vs. recibe" por local.
- **Compromiso:** HU-10, HU-11, HU-14 y HU-27 (23 puntos), más los bugs #29, #36, #38 y #44 y los chores #52 y #54.
- **Estado al 9 nov 2026:**
  - Desarrollo terminado.
  - Ya están en `main`: #45 (#38), #46 (#29), #47 (#36), #51 (#44), #53 (#52) y #55 (#54).
  - Las 4 historias están en PRs abiertos y apilados (#48 ← #49 ← #50 ← #56), actualizados con `main`.

## Completado

| Ítem | Pts | Issue | PR | Criterios | Evidencia |
|---|---|---|---|---|---|
| `GET /api/customers/{id}` solo para roles internos | – | #38 | #45 ✅ mergeado | – | `CLIENT` recibe 403 |
| Recargar la página ya no cierra la sesión | – | #29 | #46 ✅ mergeado | – | `RequireAuth` ya no decide antes de leer la sesión |
| El 401 del gateway sin el mensaje de jjwt | – | #36 | #47 ✅ mergeado | – | Mensaje fijo; el detalle va al log |
| Sin registro público y denegar por defecto | – | #44 | #51 ✅ mergeado | – | `@PreAuthorize` por endpoint; `AuthorizationIT` |
| Semilla más realista | – | #52 | #53 ✅ mergeado | – | Ruido dentro de la ventana real y la ruta de un camión |
| Ignorar `backups/` | – | #54 | #55 ✅ mergeado | 1/1 | – |
| **HU-10** Listar y filtrar envíos | 5 | #39 | #48 | 5/5 | Ver detalle abajo |
| **HU-11** Modulación vs. ventana real | 5 | #40 | #49 | 6/6 | Ver detalle abajo |
| **HU-14** Ruta del día con hora estimada | 5 | #41 | #50 | 4/4 | Ver detalle abajo |
| **HU-27** Rediseño de la pantalla de rutas | 8 | #42 | #56 | 9/9 | Ver detalle abajo |

**Puntos completados (pendientes de merge): 23/23.**

### HU-10 (#48): listado de envíos
- `GET /api/shipments` paginado, con filtros por día o rango, estado, comuna y búsqueda (sin distinguir tildes).
- Pantalla **Envíos** (`/envios`) con filtros y paginación.
- Al actualizar con `main` se conservan los permisos de #51: el listado es para `ADMIN`, `DISPATCHER` y `OPERATOR`. `AuthorizationIT` suma el listado con filtros (`CLIENT` y `DRIVER` → 403; los roles de oficina → 200).
- **Tests:** `ShipmentFilterTest` (8) y `ShipmentListIT` (7).

### HU-11 (#49): modulación vs. ventana real
- `GET /api/customers/{id}/time-window`: modulación, ventana real, "X de Y visitas", histograma por hora y horas a evitar.
- **Una sola fuente de verdad:** `TimeWindowLearningService` se extendió sin cambiar cómo elige la ventana. `TimeWindowApiIT` compara la ruta y el endpoint parada por parada.
- Detalle del local con el histograma de 07 a 22 h.
- **Tests:** HU-04 intactos, +4 en `TimeWindowLearningServiceTest`, `TimeWindowSummaryTest` (4) y `TimeWindowApiIT` (6). `AuthorizationIT` cubre `/time-window` para `CLIENT`.

### HU-14 (#50): hora estimada por parada
- `RouteTimingService`: llegada, espera hasta la ventana real, salida y "fuera de ventana", sin reordenar (`RouteOptimizationService` no se tocó).
- Tramos por calle con OSRM `/route`. Si OSRM no responde, se estima.
- En el E2E apareció un bug real: la respuesta de OSRM supera los 256 KB que acepta `WebClient`. Se corrigió en el mismo PR.
- **Tests:** `RouteTimingServiceTest` (8), `OsrmRoutingClientTest` (7) y `RoutePlanIT` (5).

### HU-27 (#56): pantalla de rutas
- Panel lateral, mapa a pantalla completa y línea de tiempo, en claro y oscuro.
- KPIs, colores por franja, detalle del local con edición de la modulación y la marca "Revisar".
- **Mapa base:** el dueño del repo decidió **Esri Gray Canvas**, configurable con `VITE_TILE_URL_LIGHT`, `VITE_TILE_URL_DARK`, `VITE_TILE_ATTRIBUTION` y `VITE_TILE_MAX_NATIVE_ZOOM`. Ahora también funcionan con Docker: llegan como build args desde `.env`, y vacías vuelven a Esri. El proveedor definitivo queda para antes de HU-24 (#57).
- **Tests:** Vitest 38 (3 nuevos para el mapa base) y E2E con Playwright 32/32.

## Objetivo del sprint

**Cumplido** en los PRs abiertos. La pantalla de rutas carga sola la ruta del día, muestra la hora estimada y la espera por parada, y el detalle de cada local muestra "Declara 9–12 · recibe 12–15 (X de Y visitas)". Falta el merge.

## Orden de merge

```
main ← #48 HU-10 ← #49 HU-11 ← #50 HU-14 ← #56 HU-27 (+ este cierre)
```

1. **#48:** HU-10, actualizado con `main`.
2. **#49:** HU-11. Incluye HU-10, para dejar resuelto que haya un solo `BusinessType` en `frontend/src/types/index.ts`.
3. **#50:** HU-14. Incluye HU-11.
4. **#56:** HU-27, con este cierre. Incluye HU-14.

Hasta que entre el PR anterior, el diff de cada uno muestra también lo de abajo. Si se borra la rama mergeada, GitHub reapunta el siguiente PR a `main`.

## Hallazgos del sprint (ver `hallazgos.md`)

| # | Issue | Tipo | Resumen |
|---|---|---|---|
| 8 | #43 | chore | Swagger público en cualquier ambiente (sigue abierto; encaja con HU-24) |
| 9 | #44 | bug | `CLIENT` podía alterar envíos e intentos. **Corregido** en #51 |
| 10 | #57 | chore | Mapa base: elegir proveedor y API key antes de HU-24 (CARTO exige key) |
| 11 | #58 | bug | La pantalla de rutas llama a `POST /api/routes/build` al abrirse (no hay `GET`): reescribe el orden y da 403 a `OPERATOR` |
| 12 | #59 | chore | El KPI de entrega en primera visita a 30 días no tiene backend |

Los hallazgos 5 (#29) y 7 (#36) quedan corregidos (#46 y #47).

## Riesgos para el Sprint 4 (HU-06, HU-07, HU-12, HU-13, HU-17)

1. **Cadena de PRs.** Hay 4 PRs apilados. Un cambio pedido en revisión en #48 hay que propagarlo con merges hacia #49, #50 y #56.
2. **HU-17 (ruta confirmada) choca con #58.** Mientras la pantalla arme la ruta al abrirse, una ruta confirmada se reordenaría. Hay que resolver #58 antes de HU-17, o dentro de ella.
3. **HU-12 y HU-13 tocan el aprendizaje.** HU-12 agrega decaimiento temporal y HU-13 una ventana base (modulación o perfil por comuna y tipo) para locales con poco historial. Las dos historias piden cambiar `TimeWindowLearningService`. La red de seguridad son los tests de HU-04 y la comparación ruta = pantalla de `TimeWindowApiIT` (HU-11). Hay que actualizar el `basis` de `/time-window` ("según modulación", "estimada por comuna").
4. **HU-12 (perfil en Redis con TTL).** La caché se debe invalidar al registrar un intento, o la pantalla y la ruta mostrarían una ventana vieja. La caché de geocodificación sigue en memoria (riesgo 7 del Sprint 2), y puede ir a Redis en la misma historia.
5. **El indicador principal sigue sin número (#59).** El Sprint 4 no lo tiene comprometido. Si la demo lo necesita, hay que planificarlo.
6. **Fin de año.** El Sprint 4 (28 dic – 8 ene) cruza Año Nuevo, así que la capacidad es menor.

## Pendiente

- Revisión y merge de #48, #49, #50 y #56 (este cierre va en #56).
- Backlog: #32, #43, #57, #58 y #59.
