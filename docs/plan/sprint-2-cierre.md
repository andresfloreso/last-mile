# Sprint 2: cierre

- **Milestone:** Sprint 2 (30 nov – 11 dic 2026)
- **Objetivo:** pruebas, importación robusta y modulación. Al cierre se debe poder importar una planilla de locales con su modulación y ver los errores por fila.
- **Compromiso:** HU-04, HU-08, HU-09 y HU-25 (23 puntos), más los bugs #9 y #12 y los ítems de seguridad #16 y #25.
- **Estado al 9 nov 2026:**
  - Desarrollo terminado.
  - Ya están en `main`: CLAUDE.md (#20), #16 (#26), #25 (#27) y #12 (#28).
  - El resto está en PRs abiertos con CI en verde.

## Completado

| Ítem | Pts | Issue | PR | Criterios | Evidencia |
|---|---|---|---|---|---|
| CLAUDE.md | – | #19 | #20 ✅ mergeado | – | Reglas del proyecto y protocolo de corte de sesión |
| Sin secretos por defecto en el código | – | #16 | #26 ✅ mergeado | 3/3 | Los 4 servicios no arrancan sin un `JWT_SECRET` de 32+ caracteres; `FailureAnalyzer` con mensaje claro |
| Seed apagado por defecto | – | #25 | #27 ✅ mergeado | 4/4 | `docker-compose.override.yml` versionado activa `seed` solo en desarrollo |
| Errores HTTP de delivery-service | – | #12 | #28 ✅ mergeado | 6/6 | 400/404/405/409/415 específicos; 500 genérico con `errorId`; `ErrorHandlingIT` (9) |
| **HU-25** Modulación declarada y tipo de local | 5 | #24 | #30 | 8/8 | Ver detalle abajo |
| **HU-08** Errores de importación por fila | 5 | #22 | #31 | 5/5 | Ver detalle abajo |
| **HU-09** Geocodificación al importar | 5 | #23 | #33 | 5/5 | Ver detalle abajo |
| **HU-04** Pruebas base por servicio | 8 | #21 | #34 | 6/6 | Ver detalle abajo |
| Gateway: headers de identidad | – | #9 | #35 | – (sin checklist) | Ver detalle abajo |

**Puntos completados (pendientes de merge): 23/23.**

### HU-25 (#30): modulación y tipo de local
- **V2:** `business_type` y modulación en dos tramos con CHECK.
- **API:** `GET /api/customers/{id}` y `PUT /api/customers/{id}/declared-window`, con springdoc.
- **Importación:** columnas `tipo_local`, `modulacion_desde` y `modulacion_hasta`; plantilla `.xlsx` descargable.
- **Seed:** 30 locales comerciales, de los cuales 11 declaran una modulación distinta de su ventana real.
- **Tests:**
  - `SeedDataIT` prueba que el motor aprende la ventana real y no la declarada.
  - `CustomerApiIT` (9 tests), `DeclaredWindowTest` y vitest.

### HU-08 (#31): errores de importación por fila
- **Resumen:** creados, omitidos y con error, cada fila con número, tracking y motivo.
- **Omitidos:** un 409 (tracking ya existente) cuenta como omitido, así que reimportar no duplica nada.
- **422:** para los problemas del archivo completo.
- **E2E:** la primera importación dio 3/1/3 (creados/omitidos/error) y la reimportación 0/4/3. En la base quedaron 3 locales y 3 envíos, sin duplicados.

### HU-09 (#33): geocodificación al importar
- **Rate limit:** 1 request por segundo, medido desde el fin de una consulta hasta el inicio de la siguiente. En el E2E los intervalos fueron 1071 ms y 1050 ms.
- **Caché:** en memoria, por dirección normalizada.
- **Marca "revisar":** si `place_rank` < 28, el local queda `REVIEW` (V3 `geocode_status`). Se ve en el resumen de importación y en la lista y el mapa de rutas.
- **Corrección:** `PUT /api/customers/{id}/location` quita la marca.
- **Tests:** contra un Nominatim falso (HttpServer local), 9 unitarios y 8 IT.

### HU-04 (#34): pruebas base por servicio
- **JWT:** tests en los 3 servicios Java.
- **Algoritmos:** `TimeWindowLearningService` (8 tests) y `RouteOptimizationService` (8 tests, con OSRM simulado).
- **Parsers:** CSV y Excel (10 tests).
- **Repositorios:** con Testcontainers (8 tests).
- **Cobertura:** JaCoCo en `algorithm/` da **99.3%** con un umbral de 60%. Se verificó que el check rompe el build si el umbral no se cumple.
- **Sin tocar los servicios:** `TimeWindowLearningService` y `RouteOptimizationService` no se modificaron.

### #9 (#35): headers de identidad en el gateway
- El gateway descarta siempre los `X-User-Id` / `X-User-Role` del cliente.
- `IdentityHeadersRoutingTest` levanta el gateway real con un servicio eco y reproduce el bug sin el fix.

## Objetivo del sprint

**Cumplido** en los PRs abiertos. Se importa una planilla con tipo de local y modulación, el resumen muestra creados, omitidos, errores y ubicaciones por revisar, y reimportar no duplica nada. Falta el merge.

## Orden de merge

```
main ← #35 fix #9                 (independiente)
main ← #30 HU-25 ← #31 HU-08 ← #33 HU-09 ← cierre (este PR)
                ↖ #34 HU-04       (solo depende de #30)
```

1. **#35**: #9 sale de `main` y no choca con nada.
2. **#30**: HU-25.
3. **#31**: HU-08 (incluye #30).
4. **#33**: HU-09 (incluye #31).
5. **#34**: HU-04 (incluye #30; no toca código de producción).
6. **Este PR** (`docs/sprint-2-cierre`): sale de HU-09.

Al mergear cada PR, GitHub reapunta el siguiente a `main` si se borra la rama mergeada.

## Hallazgos del sprint (ver `hallazgos.md`)

| # | Issue | Tipo | Resumen |
|---|---|---|---|
| 5 | #29 | bug | Recargar la página cierra la sesión: `RequireAuth` decide antes de leer `localStorage` |
| 6 | #32 | chore | La importación es síncrona: con 1 req/s, 300 direcciones nuevas tardan unos 5 min |
| 7 | #36 | bug | El 401 del gateway incluye el mensaje interno de jjwt |

Los hallazgos 1 (#9), 2 (#12) y 4 (#16) quedan corregidos.

## Riesgos para el Sprint 3 (HU-10, HU-11, HU-14, HU-27)

1. **Cadena de PRs.**
   - Hay 6 PRs por mergear, y 3 de ellos están apilados.
   - Mientras no entren, las ramas del Sprint 3 deben salir de la punta (`docs/sprint-2-cierre`, que incluye HU-25, HU-08 y HU-09).
   - Un cambio pedido en revisión hay que propagarlo con merges hacia arriba.
2. **HU-11 sin poder tocar `TimeWindowLearningService`.**
   - El endpoint `/time-window` necesita el histograma por hora y el conteo "X de Y visitas", y el servicio no los expone.
   - Habrá que calcularlos en un componente nuevo a partir de los intentos.
   - Riesgo de que diverja del motor. Mitigación: tests que comparen ambos sobre la semilla.
   - Hay que decidir si se permite extender el servicio.
3. **HU-14 (hora estimada por parada).**
   - Depende de OSRM público, que tiene límite de uso y puede no responder.
   - `RouteOptimizationService` no se puede modificar, así que la hora estimada va en una capa aparte.
   - Ahora hay 8 tests del orden como red de seguridad.
4. **HU-27 rediseña la pantalla de rutas** que HU-09 acaba de tocar (carga de envíos de la fecha y marca "Revisar"). El rediseño debe conservar esa marca. Las referencias `docs/design/rutas-claro.png` y `rutas-oscuro.png` siguen sin trackear, como se pidió.
5. **#29 (la sesión se pierde al recargar)** molesta en las demos y en las capturas de Playwright, que deben navegar sin recargar. Conviene corregirlo temprano en el Sprint 3, aunque HU-06 sea del Sprint 4.
6. **Fiestas de fin de año.** El Sprint 3 termina el 25 de diciembre, así que la capacidad es menor. El plan ya prevé un 20% menos de carga.
7. **Caché de geocodificación en memoria.** Se pierde al reiniciar delivery-service y no se comparte entre instancias. Encaja con Redis en HU-12 (Sprint 4).

## Pendiente

- Revisión y merge de #35, #30, #31, #33, #34 y este PR.
- Backlog: #29, #32 y #36.
