# HU-32: medición antes y después

Medido el 2026-11-10 sobre la semilla de HU-31, con la ruta de hoy (24
paradas) y la del día cargado (2027-01-04, 40 paradas).

- **Antes:** el código de HU-28 (#76).
- **Después:** esta rama.
- **Mismo stack, mismo navegador:** Chromium headless de Playwright, 1440×900.

## Cómo se midió

- **Consultas a OSRM.** `delivery-service` apuntó a
  [`infra/seed/tools/contar_osrm.py`](../../../infra/seed/tools/contar_osrm.py)
  con `OSRM_URL`. Es un proxy que reenvía cada consulta al OSRM público y la
  cuenta por servicio (`table`, `route`).
- **La pantalla.** [`medir-pantalla.js`](medir-pantalla.js) (Playwright)
  hace tres cosas:
  1. **Abrir.** Entra como despacho, abre el día cargado, cambia a hoy, vuelve
     al día cargado y recarga la página: 4 aperturas.
  2. **Mover el mapa.** Hace 12 arrastres de 250 px y 4 zooms (dos de acercar
     y dos de alejar), con la CPU limitada ×4 para parecerse a un notebook de
     oficina.
     - Cuenta los cuadros con `requestAnimationFrame`.
     - Lee de Chrome (CDP `Performance.getMetrics`) cuánto tiempo pasó
       ejecutando JavaScript y cuánto en estilo y layout.
  3. **Operador.** Entra como operaciones y abre la pantalla.
- Dos corridas de cada lado. Los datos están en
  [`mediciones.json`](mediciones.json).

## Resultado

### Abrir la pantalla 4 veces

| | Antes | Después |
|---|---|---|
| Llamadas a la API | 4 × `POST /api/routes/build` | 4 × `GET /api/routes` |
| Escrituras en la base | 4 (reordena y deja los envíos en `ROUTED`) | 0 |
| Consultas a OSRM | **12** (8 `table` + 4 `route`) | **2** con la caché vacía, **0** con la caché llena |

Antes, cada apertura armaba la ruta: el nearest-neighbor pedía una `table`, el
VRPTW otra y la hora estimada una `route`. Ahora, abrir solo lee la ruta
guardada y le pone hora. La `route` de un mismo orden queda en una caché en
memoria (`routing.osrm-route-cache`: 64 rutas, 6 h). Por eso solo la primera
apertura de cada día consulta OSRM; las siguientes no consultan, hasta que el
orden cambie, venza la entrada o se reinicie el servicio. La segunda corrida
se hizo sin reiniciar el servicio: por eso son 0.

### Mover el mapa (12 arrastres y 4 zooms, CPU ×4)

| | Antes (corrida 1 / 2) | Después (corrida 1 / 2) |
|---|---|---|
| Consultas a OSRM | 0 / 0 | 0 / 0 |
| Llamadas a la API | 0 / 0 | 0 / 0 |
| JavaScript | 1.876 / 2.208 ms | **678 / 777 ms** (−64 %) |
| Estilo y layout | 2.046 / 2.320 ms | **1.237 / 1.241 ms** (−44 %) |
| Duración total | 14,3 / 16,1 s | 11,3 / 11,8 s |
| Cuadro mediano / p95 | 16,7 / 33,3 ms | 16,7 / 33,3 ms |
| Cuadros de más de 50 ms | 6 / 10 | 5 / 10 |
| Tareas largas | 232 / 351 ms | 266 / 513 ms |

- Mover el mapa **no consultaba OSRM ni antes ni después**: la geometría
  viene en la respuesta de la ruta. El criterio se cumple, y queda medido.
- Lo que sí costaba era React:
  - antes, cada evento `move` del mapa volvía a dibujar el componente del mapa
    completo, con sus 40 marcadores y la polilínea, aunque no hubiera ninguna
    parada seleccionada;
  - ahora, solo la línea guía de la tarjeta sigue al mapa, y solo con una
    parada seleccionada.

  Por eso el tiempo de JavaScript baja a un tercio.
- La polilínea se dibuja en canvas (`preferCanvas`). Leaflet la simplifica en
  cada zoom con una tolerancia de 1,5 px (`smoothFactor`).
- La mediana de cuadros no cambia, porque el navegador ya llegaba a 60 cuadros
  por segundo. Las tareas largas y los cuadros lentos son ruido entre corridas
  (vienen de la carga de teselas): no muestran ni mejora ni empeoramiento.

### Operador

| | Antes | Después |
|---|---|---|
| Al abrir la pantalla | `POST /api/routes/build` → **403** ("Tu rol no puede armar rutas") | `GET /api/routes` → **200**, ruta en solo lectura |

## Roles de la pantalla de rutas

| Rol | Ve la ruta guardada (`GET /api/routes`) | Arma la ruta (`POST /api/routes/build`) | Pantalla |
|---|---|---|---|
| ADMIN | ✓ | ✓ | Completa |
| DISPATCHER | ✓ | ✓ | Completa |
| OPERATOR | ✓ | — | Solo lectura: sin "Reoptimizar" ni "Armar ruta" |
| DRIVER | — | — | Mensaje: "Tu rol no ve la ruta del día" |
| CLIENT | — | — | Mensaje: "Tu rol no ve la ruta del día" |

## Para repetirla

```bash
python infra/seed/tools/contar_osrm.py 5099 &
# delivery-service con OSRM_URL=http://host.docker.internal:5099
# (y extra_hosts host.docker.internal:host-gateway en Linux)
cd <carpeta con playwright 1.48> && TAG=antes node docs/plan/hu-32/medir-pantalla.js
```
