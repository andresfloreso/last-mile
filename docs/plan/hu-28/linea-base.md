# HU-28: línea base del nearest-neighbor

Medida **antes de escribir el optimizador**, el 2026-11-09, sobre la semilla
final del Sprint 4 (HU-31, con paradas de HU-30 y carga de HU-29).

## Cómo se midió

- Stack completo con `docker compose`, perfil `seed` y OSRM público (tiempos y
  km por calle, `timingSource = OSRM`).
- `python infra/seed/tools/medir_ruta.py <fecha> --json <salida>`:
  - arma la ruta con `POST /api/routes/build` como el despachador semilla;
  - sale de la bodega (Av. Italia 1300) a las 08:00;
  - usa 10 min por parada.
- Dos fechas:
  - **hoy**: las 24 paradas de la demo; se generan con la fecha del día, con el mismo patrón;
  - **día cargado**: el 2027-01-04, con 40 paradas y 46 envíos.
- Cada ruta (orden, ventana, horas y metros de cada tramo, una parada por
  línea y sin la geometría de OSRM) está en
  [`nn-hoy.json`](nn-hoy.json) y [`nn-dia-cargado.json`](nn-dia-cargado.json).

## Resultado

| Día | Paradas | km | Hora de término | Paradas fuera de ventana | Minutos fuera de ventana |
|---|---|---|---|---|---|
| Hoy | 24 | 51,7 | 19:19 | 0 | 0 |
| Día cargado (2027-01-04) | 40 | 72,7 | 20:14 | 4 | 4 |

- **Hora de término:** la salida de la última parada.
- **Paradas fuera de ventana:** paradas con `outsideWindow`, es decir, la
  entrega empieza a la hora de cierre de la ventana real o después.
- **Minutos fuera de ventana:** la suma, en esas paradas, de los minutos entre
  el cierre y el inicio de la entrega, truncados al minuto. Una entrega que
  empieza a las 11:00:30 con cierre a las 11:00 cuenta como fuera de ventana,
  pero suma 0 minutos.

## Lo que muestra

El nearest-neighbor agrupa las paradas por franja de la ventana real y dentro
de cada franja elige la más cercana. En el día cargado:

- **Las 4 paradas fuera de ventana son la última de cada franja.**
  - La 10 (*Minimarket Plaza Ñuñoa*) a las 11:00.
  - La 20 (*Botillería Los Leones*) a las 14:00.
  - La 30 (*Restaurant San Diego*) a las 17:00.
  - La 40 (*Bar La Esquina de Ñuñoa*) a las 20:04.
  - Llena cada franja hasta el borde y deja para el final la parada más lejana.
- **El recorrido cruza la zona varias veces.** Las franjas no se mezclan,
  así que entre una y otra el camión vuelve a cruzar la zona y espera a que
  abra la siguiente (por ejemplo, 56 min antes de la primera parada).

Capturas de la ruta, con el mismo zoom que tendrán las de "después":

| Hoy | Día cargado |
|---|---|
| ![hoy](https://raw.githubusercontent.com/andresfloreso/last-mile/assets/pr-screenshots/hu28-antes-hoy.png) | ![cargado](https://raw.githubusercontent.com/andresfloreso/last-mile/assets/pr-screenshots/hu28-antes-cargado.png) |
