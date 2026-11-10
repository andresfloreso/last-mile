# Sprint 4: cierre

- **Milestone:** Sprint 4 (28 dic 2026 – 8 ene 2027)
- **Objetivo:** rutas de calidad. Al cierre, la ruta del día:
  - se arma sobre direcciones reales, con una parada por local;
  - respeta la capacidad del camión;
  - tiene menos km y menos paradas fuera de ventana que el nearest-neighbor, según la tabla antes/después.
- **Compromiso:** HU-31, HU-30, HU-29, HU-28 y HU-32, 22 puntos. #58 va dentro de HU-32.
- **Estado al 10 nov 2026:**
  - Desarrollo terminado.
  - En `main`: #72 (HU-31), #73 (HU-30) y #74 (HU-29).
  - HU-28 (#76) y HU-32 están en PRs abiertos y apilados.

## Completado

| Ítem | Pts | Issue | PR | Criterios | Evidencia |
|---|---|---|---|---|---|
| **HU-31** Semilla con direcciones reales | 3 | #63 | #72 ✅ mergeado | 7/7 | Locales con direcciones reales en una zona compacta; hoy con 24 paradas y un día cargado (2027-01-04) |
| **HU-30** Una parada por local | 3 | #62 | #73 ✅ mergeado | 4/4 | Hoy: 26 envíos en 24 paradas; día cargado: 46 en 40 |
| **HU-29** Vehículos y capacidad | 5 | #61 | #74 ✅ mergeado | 6/6 | Dos camiones en la semilla; peso y volumen por envío |
| **HU-28** Optimizador VRPTW | 8 | #60 | #76 | 10/10 | Tabla antes/después abajo |
| **HU-32** Mapa y ruta sin efectos | 3 | #64 (y #58) | PR de HU-32 | 6/6 | `docs/plan/hu-32/medicion.md` |

**Puntos completados: 22/22.** 11 ya están en `main`; los 11 de HU-28 y HU-32 esperan merge.

### HU-28 (#76): optimizador VRPTW

- `RouteOptimizer` con dos implementaciones:
  - Timefold (VRPTW de un camión, con búsqueda tabú);
  - nearest-neighbor, que es el respaldo y la línea base, sin cambios.
- La línea base se guardó antes de escribir el optimizador (`docs/plan/hu-28/linea-base.md`).
- Medición con OSRM real:

| Día | km | Hora de término | Paradas fuera de ventana | Minutos fuera de ventana |
|---|---|---|---|---|
| Hoy (24 paradas): nearest-neighbor → VRPTW | 51,7 → **48,0** | 19:19 → 19:19 | 0 → 0 | 0 → 0 |
| Día cargado (40 paradas): nearest-neighbor → VRPTW | 72,7 → **69,9** | 20:14 → **20:11** | 4 → **3** | 4 → **1** |

- **Tests:**
  - `RouteRegressionIT` falla si el VRPTW deja más paradas fuera de ventana que la línea base, o si tarda 15 s o más;
  - `TimefoldRouteOptimizerTest` (9) y `TruckRouteTest` (8);
  - JaCoCo: `algorithm/` 99,4 % y `algorithm/optimizer/` 97,3 %.
- **Jar de `delivery-service`:** 68,2 → 75,6 MB, por Timefold y sus dependencias.

### HU-32: ruta guardada y mapa más liviano

- `GET /api/routes` devuelve la ruta guardada con su hora estimada, sin escribir nada. La pantalla lo usa al abrir y solo llama a `build` cuando despacho lo pide.
- Roles: `ADMIN` y `DISPATCHER` ven y arman la ruta; `OPERATOR` la ve en solo lectura (antes recibía 403); `DRIVER` y `CLIENT` no la ven.
- Caché en memoria de OSRM `/route` (64 rutas, 6 h).
- Mapa con `preferCanvas`, polilínea simplificada por zoom, y sin volver a dibujar los marcadores en cada movimiento.
- Medición antes/después:

| | Antes | Después |
|---|---|---|
| Consultas a OSRM al abrir la pantalla 4 veces | 12 | 2 con la caché vacía; 0 con la caché llena |
| Consultas a OSRM al mover el mapa | 0 | 0 |
| JavaScript al mover el mapa (CPU ×4) | 1.876 / 2.208 ms | 678 / 777 ms |
| `OPERATOR` al abrir la pantalla | 403 | 200, en solo lectura |

## Objetivo del sprint

**Cumplido.** En el día cargado, la ruta tiene 2,8 km menos y una parada menos
fuera de ventana (4 → 3; 4 → 1 minutos). Hoy tiene 3,7 km menos, con todas las
paradas a tiempo. La pantalla de rutas ya no reescribe la ruta al abrirse.
Falta el merge de #76 y del PR de HU-32.

## Orden de merge

```
main (#72, #73, #74 ya mergeados) ← #76 HU-28 ← PR de HU-32 (+ este cierre)
```

1. **#76, HU-28.** Ya tiene `main` mergeado: su diff muestra solo HU-28.
2. **PR de HU-32.** La rama salió de HU-28, porque cambia `RouteController` y
   `RouteService`, que HU-28 también toca. Hasta que entre #76, el diff de
   HU-32 muestra también lo de HU-28.

## Pendiente

Nada del compromiso. Fuera del sprint siguen abiertos estos hallazgos: #70,
#71 y #75 (de este sprint), y #43, #57 y #59 (del anterior).

## Hallazgos del sprint (ver `hallazgos.md`)

| # | Issue | Tipo | Resumen |
|---|---|---|---|
| 13 | #70 | bug | `build` deja en `ROUTED` envíos ya cerrados (`DELIVERED`, `RESCHEDULED`) si se arma la ruta de un día pasado. Con HU-32 la pantalla ya no arma al abrir, así que pasa solo si despacho lo pide; el bug sigue |
| 14 | #71 | bug | La semilla calcula "hoy" en UTC: entre las 21:00 y las 24:00 de Chile crea los envíos de mañana |
| 15 | #75 | bug | El solver (con `/table`) y la hora estimada (con `/route`) cuentan distinto las paradas que llegan en el minuto del cierre: el día cargado muestra 3 fuera de ventana en vez de 1. El margen de 2 min queda para ese issue, por decisión del dueño del repo |

El hallazgo 11 (#58) queda resuelto con HU-32.

## Riesgos del Sprint 5

El Sprint 5 trae HU-06, HU-07, HU-12, HU-13 y HU-17: 24 puntos.

- **HU-17 (ruta confirmada) se apoya en HU-32.** La ruta guardada todavía no
  guarda con qué optimizador ni con qué camión se armó: `GET /api/routes`
  devuelve `optimizer` y `vehiclePlate` en `null`. Al confirmar una ruta hay
  que guardar ese dato, y `build` no debe poder reordenar una ruta confirmada.
- **HU-12 (caché del perfil en Redis) cambia lo que ve el optimizador.** Si la
  caché del perfil queda vieja, el VRPTW ordena con una ventana real antigua.
  `RouteRegressionIT` y la tabla de HU-28 sirven de control: hay que volver a
  correrlos al terminar HU-12.
- **#70 conviene cerrarlo antes de HU-17.** Una ruta confirmada no debería
  cambiar el estado de envíos ya entregados.
- **OSRM público.** La caché de HU-32 baja las consultas, pero armar la ruta
  sigue pidiendo 2 tablas y una ruta. Un OSRM propio sigue pendiente para
  antes de HU-24.
