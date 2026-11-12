# HU-12: decaimiento temporal y perfil en Redis

## Qué cambia

- **Decaimiento:** cada intento pesa `0,5^(antigüedad / vida media)` en el
  puntaje con que se elige la ventana real. La vida media se configura con
  `time-window.decay.half-life-days` (`TIME_WINDOW_HALF_LIFE_DAYS`): por
  defecto 90 días, y 0 la apaga.
- **Lo que no cambia con el decaimiento:** los conteos de la explicación
  ("recibió X de Y visitas", HU-11) siguen siendo visitas reales, y la
  confianza sigue contando intentos.
- **Caché:** el perfil de cada local se guarda en Redis
  (`lastmile:ventana-real:v1:<id>`, JSON) con TTL de 6 h
  (`TIME_WINDOW_CACHE_TTL`).
  - Registrar un intento lo invalida dos veces: al guardar y al confirmar la
    transacción.
  - Al arrancar, el servicio borra todos los perfiles, porque la semilla o una
    migración pudieron cambiar los intentos sin pasar por la invalidación.
  - Si Redis no responde, el perfil se calcula como antes. La espera es de
    500 ms como máximo, y Redis no cuenta para el health check.
  - `TIME_WINDOW_CACHE_ENABLED=false` apaga la caché.

## Por qué la vida media es de 90 días y no menos

Se simuló el algoritmo con los 800 intentos de la semilla:

| Vida media | Locales de la semilla que cambian de ventana real |
|---|---|
| 30, 45 o 60 días | **20 de 40** |
| 90, 120, 180 o 365 días | 0 de 40 |

**La causa:** la semilla pone los rechazos "Encargado no estaba", que caen
dentro de la ventana, en las visitas más recientes. Por ejemplo, Botillería
El Roble tiene sus 3 rechazos en los últimos 16 días. Con una vida media
corta, ese ruido reciente pesa como un cambio de hábito y la ventana se corre
una hora. `SeedDataIT` lo detecta.

Con 90 días, la semilla se aprende igual que antes, y un cambio de hábito real
igual se aprende. En `TimeWindowDecayAndCacheTest`, un local que recibió de 9
a 11 durante meses y en el último mes recibe de 15 a 17:

- con el decaimiento, aprende 15–17;
- sin el decaimiento, sigue en 9–11.

## Control de la ruta (como pidió el cierre del Sprint 4)

`RouteRegressionIT`: en verde, con 2 de 2 tests.

`infra/seed/tools/medir_ruta.py` en un stack limpio con esta rama y OSRM
público. Usa VRPTW, salida 08:00 y el camión KXPT-21:

| Día | | km | Término | Fuera de ventana | Min fuera |
|---|---|---|---|---|---|
| Hoy (24 paradas) | HU-28 | 48,0 | 19:19 | 0 | 0 |
| | **HU-12** | **48,0** | **19:19** | **0** | **0** |
| Día cargado (40 paradas) | HU-28 | 69,9 | 20:11 | 3 | 1 |
| | **HU-12** | **69,9** | **20:11** | **3** | **1** |

Las rutas quedan iguales, porque las ventanas reales de la semilla no
cambian. El detalle está en [`vrptw-hoy.json`](vrptw-hoy.json) y
[`vrptw-dia-cargado.json`](vrptw-dia-cargado.json).

## Lo que ahorra la caché

`GET /api/routes` del día cargado (40 locales), 5 llamadas en cada caso, con
la `route` de OSRM ya en la caché de HU-32:

| | Tiempo de respuesta |
|---|---|
| Sin perfiles en Redis (se calculan los 40) | 123–168 ms |
| Con los perfiles en Redis | 57–84 ms |
