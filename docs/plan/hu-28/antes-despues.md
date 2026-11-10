# HU-28: antes y después del optimizador VRPTW

Misma medición que la [línea base](linea-base.md), con el mismo script, la
misma semilla, OSRM público y la misma configuración: salida 08:00 desde la
bodega, 10 min por parada. El "después" usa el VRPTW con Timefold y la
configuración de producción: 10 s como máximo, o 3 s sin mejora. El camión
es el KXPT-21 (Camión 3/4: 3.500 kg, 18 m³, jornada 08:00–21:00).

Medido el 2026-11-10 con `python infra/seed/tools/medir_ruta.py <fecha> --json <salida>`.

## Resultado

| Día | Paradas | km | Hora de término | Paradas fuera de ventana | Minutos fuera de ventana | Optimizador |
|---|---|---|---|---|---|---|
| Hoy, antes (nearest-neighbor) | 24 | 51,7 | 19:19 | 0 | 0 | — |
| **Hoy, después (VRPTW)** | 24 | **48,0** (−3,7 km; −7 %) | 19:19 | 0 | 0 | `VRPTW` |
| Día cargado, antes (nearest-neighbor) | 40 | 72,7 | 20:14 | 4 | 4 | — |
| **Día cargado, después (VRPTW)** | 40 | **69,9** (−2,8 km; −4 %) | **20:11** | **3** | **1** | `VRPTW` |

Las respuestas completas están en [`vrptw-hoy.json`](vrptw-hoy.json) y
[`vrptw-dia-cargado.json`](vrptw-dia-cargado.json). Las de antes están en
[`nn-hoy.json`](nn-hoy.json) y [`nn-dia-cargado.json`](nn-dia-cargado.json).

## Lo que cambió

- **Hoy:** el nearest-neighbor ya dejaba todas las paradas a tiempo. El VRPTW
  mantiene eso y acorta el recorrido 3,7 km. La hora de término no cambia,
  porque la última franja (bares, hasta las 20:00) fija cuándo termina la
  ruta: el camión espera a que abran.
- **Día cargado:** quedan 3 paradas fuera de ventana (antes 4). Suman 1
  minuto (antes 4):
  - *Minimarket Plaza Ñuñoa*, entrega a las 11:00, cierre 11:00;
  - *Botillería Los Leones*, entrega a las 14:00, cierre 14:00;
  - *Bar El Clavel*, entrega a las 20:01, cierre 20:00.

  Las tres llegan en el minuto del cierre, no horas tarde como podría pasar
  con un orden malo. La ruta termina 3 min antes.

## Por qué no llega a cero en el día cargado

Las franjas de la semilla están llenas a propósito: 10 paradas de 10 min por
franja de 2 h, más los traslados. Un cálculo exacto por franja (programación
dinámica, fuera del repo) con las mismas tablas de OSRM dio como mejor
resultado 1 parada fuera de ventana y 68,2 km.

El modelo del solver ve 1 parada fuera de ventana; el horario de
`RouteTimingService` ve 3. La diferencia es de segundos:

- el solver usa la tabla de OSRM (`/table`, de punto a punto);
- el horario usa los tramos de `/route`, que pueden durar unos segundos más.

Dos paradas que el solver deja segundos antes del cierre quedan en el horario
segundos después de él, y se cuentan como fuera de ventana con 0 minutos. Queda registrado como hallazgo
([#75](https://github.com/andresfloreso/last-mile/issues/75)): dejar un margen
en el modelo o usar la misma fuente de tiempos en los dos lados.

## Búsqueda: late acceptance vs. tabú

Con la búsqueda local por defecto de Timefold (late acceptance), el día
cargado terminaba en 79,3 km: peor en km que el nearest-neighbor, aunque con
menos paradas fuera de ventana. La causa es un óptimo local, porque cada
parada fuera de ventana cuesta un salto grande del puntaje. Con búsqueda tabú
(`entityTabuSize` 1, `valueTabuSize` 7, 1.000 movimientos aceptados por paso)
llega a 69,9 km. Por eso `TimefoldRouteOptimizer.localSearch()` usa tabú.

## Tiempo de resolución y tamaño del jar

- **Tiempo:** el solver corta a los 10 s, o antes si pasan 3 s sin mejora. En
  `RouteRegressionIT`, con tiempos estimados, hoy tarda unos 4 s y el día
  cargado unos 12 s, incluida la carga de datos. El test falla si una ruta
  tarda 15 s o más.
- **Tamaño:** `timefold-solver-core` 1.15.0 (Apache 2.0, edición Community)
  y sus dependencias suman 7,4 MB al jar de `delivery-service`, que pasa de
  68,2 MB a 75,6 MB. Las dependencias son `commons-math3`, `micrometer-core`
  y JAXB.

## Capturas

Mismo viewport (1440×900) y mismo zoom automático del mapa que las de antes.

| | Hoy | Día cargado |
|---|---|---|
| Antes | ![antes hoy](https://raw.githubusercontent.com/andresfloreso/last-mile/assets/pr-screenshots/hu28-antes-hoy.png) | ![antes cargado](https://raw.githubusercontent.com/andresfloreso/last-mile/assets/pr-screenshots/hu28-antes-cargado.png) |
| Después | ![después hoy](https://raw.githubusercontent.com/andresfloreso/last-mile/assets/pr-screenshots/hu28-despues-hoy.png) | ![después cargado](https://raw.githubusercontent.com/andresfloreso/last-mile/assets/pr-screenshots/hu28-despues-cargado.png) |
